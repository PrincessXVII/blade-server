package dev.systemcrash.blade.cosmetics.titlecolor;

import java.util.List;

/** Applies a multi-stop hex gradient to text using LuckPerms/TAB legacy hex (`&#RRGGBB`). */
public final class TitleGradient {
    private TitleGradient() {
    }

    public static String colorizeLegacy(String text, List<String> hexStops) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (hexStops == null || hexStops.isEmpty()) {
            return "&f" + text;
        }
        StringBuilder out = new StringBuilder(text.length() * 10);
        int n = text.length();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0.0 : (double) i / (double) (n - 1);
            out.append("&#").append(sample(hexStops, t)).append(text.charAt(i));
        }
        return out.toString();
    }

    public static String previewMini(String text, List<String> hexStops) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (hexStops == null || hexStops.isEmpty()) {
            return "<white>" + text + "</white>";
        }
        StringBuilder out = new StringBuilder(text.length() * 24);
        int n = text.length();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0.0 : (double) i / (double) (n - 1);
            out.append("<color:#").append(sample(hexStops, t)).append('>')
                    .append(text.charAt(i)).append("</color>");
        }
        return out.toString();
    }

    private static String sample(List<String> stops, double t) {
        if (stops.size() == 1) {
            return normalize(stops.get(0));
        }
        double scaled = t * (stops.size() - 1);
        int i = (int) Math.floor(scaled);
        int j = Math.min(stops.size() - 1, i + 1);
        double local = scaled - i;
        int[] a = rgb(stops.get(i));
        int[] b = rgb(stops.get(j));
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * local);
        int g = (int) Math.round(a[1] + (b[1] - a[1]) * local);
        int bl = (int) Math.round(a[2] + (b[2] - a[2]) * local);
        return String.format("%02X%02X%02X", clamp(r), clamp(g), clamp(bl));
    }

    private static int[] rgb(String hex) {
        String h = normalize(hex);
        return new int[]{
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16)
        };
    }

    private static String normalize(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return h.toUpperCase();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
