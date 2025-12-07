package org.example.stackoverflowjavaanalysis.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.stackoverflowjavaanalysis.data.model.ApiQuestion;
import java.util.List;

public class StackOverflowResponse {
    @JsonProperty("items")
    private List<ApiQuestion> items;

    @JsonProperty("has_more")
    private boolean hasMore;

    public List<ApiQuestion> getItems() { return items; }
    public void setItems(List<ApiQuestion> items) { this.items = items; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
}