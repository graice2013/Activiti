/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.app.servlet;

import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

import org.activiti.app.conf.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
// 实现ServletContextListener接口
public class WebConfigurer implements ServletContextListener {
	
    private final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

    public AnnotationConfigWebApplicationContext context;
    
    public void setContext(AnnotationConfigWebApplicationContext context) {
        this.context = context;
    }

    // 容器初始化方法
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.debug("Configuring Spring root application context");

        ServletContext servletContext = sce.getServletContext();

        AnnotationConfigWebApplicationContext rootContext = null;
        
        // 当上下文环境的容器为空时，构建Spring容器
        if (context == null) {
            rootContext = new AnnotationConfigWebApplicationContext();
            // 定义一个Spring根容器，配置为ApplicationConfiguration
            rootContext.register(ApplicationConfiguration.class);
            
            // 绑定servelet容器到Spring根容器里面
            if (rootContext.getServletContext() == null) {
              rootContext.setServletContext(servletContext);
            }
            
            rootContext.refresh();
            context = rootContext;
            
        } else {
            rootContext = context;
            if (rootContext.getServletContext() == null) {
              rootContext.setServletContext(servletContext);
            }
        }
        
        // 将Spring根容器作为一个属性添加到servlet容器中
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, rootContext);

        EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);

        initSpring(servletContext, rootContext);
        initSpringSecurity(servletContext, disps);

        log.debug("Web application fully configured");
    }

    /**
     * Initializes Spring and Spring MVC.
     */
    private void initSpring(ServletContext servletContext, AnnotationConfigWebApplicationContext rootContext) {
        log.debug("Configuring Spring Web application context");
        // 定义 appDispatcher子容器
        AnnotationConfigWebApplicationContext appDispatcherServletConfiguration = new AnnotationConfigWebApplicationContext();
        appDispatcherServletConfiguration.setParent(rootContext);
        // appDispatcher子容器配置文件
        appDispatcherServletConfiguration.register(AppDispatcherServletConfiguration.class);

        log.debug("Registering Spring MVC Servlet");
        // 注册子容器到Spring Servlet中
        ServletRegistration.Dynamic appDispatcherServlet = servletContext.addServlet("appDispatcher", 
                new DispatcherServlet(appDispatcherServletConfiguration));
        // 请求映射
        appDispatcherServlet.addMapping("/app/*");
        // 加载优先级
        appDispatcherServlet.setLoadOnStartup(1);
        // 支持异步
        appDispatcherServlet.setAsyncSupported(true);
        
        // 定义 apiDispatcher子容器
        log.debug("Registering Activiti public REST API");
        AnnotationConfigWebApplicationContext apiDispatcherServletConfiguration = new AnnotationConfigWebApplicationContext();
        apiDispatcherServletConfiguration.setParent(rootContext);
        // apiDispatcher子容器配置文件
        apiDispatcherServletConfiguration.register(ApiDispatcherServletConfiguration.class);

        ServletRegistration.Dynamic apiDispatcherServlet = servletContext.addServlet("apiDispatcher",
                new DispatcherServlet(apiDispatcherServletConfiguration));
        apiDispatcherServlet.addMapping("/api/*");
        apiDispatcherServlet.setLoadOnStartup(1);
        apiDispatcherServlet.setAsyncSupported(true);
    }

    /**
     * Initializes Spring Security.
     */
    private void initSpringSecurity(ServletContext servletContext, EnumSet<DispatcherType> disps) {
        log.debug("Registering Spring Security Filter");
        FilterRegistration.Dynamic springSecurityFilter = servletContext.addFilter("springSecurityFilterChain", new DelegatingFilterProxy());

        springSecurityFilter.addMappingForUrlPatterns(disps, false, "/*");
        springSecurityFilter.setAsyncSupported(true);
    }


    // 容器销毁方法
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Destroying Web application");
        WebApplicationContext ac = WebApplicationContextUtils.getRequiredWebApplicationContext(sce.getServletContext());
        AnnotationConfigWebApplicationContext gwac = (AnnotationConfigWebApplicationContext) ac;
        gwac.close();
        log.debug("Web application destroyed");
    }
}
