package com.jaden.imagemeta;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

/** Pure validation, independent of Android, shared by the form and writer. */
public final class EditValues {
    public static double[] coordinates(String latitude, String longitude) {
        latitude = latitude.trim(); longitude = longitude.trim();
        if (latitude.isEmpty() && longitude.isEmpty()) return null;
        if (latitude.isEmpty() || longitude.isEmpty()) throw new IllegalArgumentException("经纬度需要同时填写，或同时留空以删除。");
        try {
            double lat = Double.parseDouble(latitude), lon = Double.parseDouble(longitude);
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
                throw new IllegalArgumentException("纬度范围是 -90～90，经度范围是 -180～180。");
            return new double[]{lat, lon};
        } catch (NumberFormatException e) { throw new IllegalArgumentException("经纬度请填写十进制数字，例如 31.2304、121.4737。"); }
    }
    public static void validate(Map<String, String> values) {
        coordinates(values.getOrDefault("latitude", ""), values.getOrDefault("longitude", ""));
        for (String key : new String[]{"DateTimeOriginal", "DateTime"}) {
            String s = values.getOrDefault(key, "").trim();
            if (!s.isEmpty()) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                f.setLenient(false); ParsePosition p = new ParsePosition(0);
                if (!s.matches("\\d{4}:\\d{2}:\\d{2} \\d{2}:\\d{2}:\\d{2}") || f.parse(s, p) == null || p.getIndex() != s.length())
                    throw new IllegalArgumentException("时间格式应为 yyyy:MM:dd HH:mm:ss，例如 2026:10:13 15:30:00。");
            }
        }
        String url = values.getOrDefault("sourceUrl", "").trim();
        if (!url.isEmpty() && !(url.startsWith("https://") || url.startsWith("http://")))
            throw new IllegalArgumentException("来源网址应以 https:// 或 http:// 开头。");
        int length = 0;
        for (String value : values.values()) length += value.length();
        if (length > 6000) throw new IllegalArgumentException("编辑内容过长，请将总长度控制在 6000 个字符以内。");
    }
}
