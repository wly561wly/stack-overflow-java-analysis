package org.example.stackoverflowjavaanalysis.util;

public class StringUtils {
    // 长内容截取阈值
    private static final int CONTENT_TRUNCATE_LENGTH = 200;

    /**
     * 截取长内容，超出部分用"..."代替
     */
    public static String truncateLongContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // 先移除HTML标签（避免标签占长度）
        String cleanContent = content.replaceAll("<[^>]*>", "");
        if (cleanContent.length() <= CONTENT_TRUNCATE_LENGTH) {
            return cleanContent;
        }
        return cleanContent.substring(0, CONTENT_TRUNCATE_LENGTH) + "...";
    }
}