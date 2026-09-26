package com.salesforce.sync.multitenancy;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class OrganizationContextFilter implements Filter {

    public static final String ORG_HEADER = "X-Organization-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            try {
                String orgHeader = httpRequest.getHeader(ORG_HEADER);
                if (orgHeader != null && !orgHeader.isBlank()) {
                    OrganizationContext.setCurrentOrganization(orgHeader);
                }
                chain.doFilter(request, response);
            } finally {
                OrganizationContext.clear();
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
