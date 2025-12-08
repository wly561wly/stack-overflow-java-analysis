package org.example.stackoverflowjavaanalysis.data.model;

import jakarta.persistence.*;

@Entity
@Table(name = "topics")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    // 新增：相关关键词，逗号分隔存储，例如 "java,jdk,jvm"
    private String relatedKeywords;

    public Topic() {}
    
    public Topic(String name, String description, String relatedKeywords) {
        this.name = name;
        this.description = description;
        this.relatedKeywords = relatedKeywords;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRelatedKeywords() { return relatedKeywords; }
    public void setRelatedKeywords(String relatedKeywords) { this.relatedKeywords = relatedKeywords; }
}