package org.fuzzypattern;

import org.apache.camel.Producer;
import org.apache.camel.http.common.UrlRewrite;
import org.springframework.stereotype.Component;

@Component
public class Rewriter implements UrlRewrite {
    public String rewrite(String url, String relativeUrl, Producer producer) throws Exception  {
        return relativeUrl;
    }
}
