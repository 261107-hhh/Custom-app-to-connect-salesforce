package com.salesforce.sync.model.dto;

public class CreateOrgRequest {
    private String name;
    private String slug;

    public CreateOrgRequest() {}

    public CreateOrgRequest(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
