/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Very small regex based highlighter for Java, XML and similar text. */
public final class SyntaxHighlighter {

    private static final Pattern JAVA = Pattern.compile(
            "(?s)(/\\*.*?\\*/)|(//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\")|('(?:\\\\.|[^'\\\\\\n])*')"
                    + "|\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null)\\b"
                    + "|(@[A-Za-z_][A-Za-z0-9_.]*)|\\b(0[xX][0-9a-fA-F]+[lL]?|\\d+(?:\\.\\d+)?[fFdDlL]?)\\b");

    private static final Pattern XML = Pattern.compile(
            "(?s)(<!--.*?-->)|(\"[^\"]*\")|(</?[A-Za-z_][A-Za-z0-9_.:\\-]*)|(\\s[A-Za-z_][A-Za-z0-9_.:\\-]*=)|(/?>)");

    private static final Pattern JSON = Pattern.compile(
            "(\"(?:\\\\.|[^\"\\\\\\n])*\"\\s*:)|(\"(?:\\\\.|[^\"\\\\\\n])*\")|\\b(true|false|null)\\b|(-?\\d+(?:\\.\\d+)?)");

    private SyntaxHighlighter() {
    }

    public static boolean supports(String ext) {
        return "java".equals(ext) || "kt".equals(ext) || "xml".equals(ext) || "json".equals(ext)
                || "gradle".equals(ext) || "html".equals(ext) || "smali".equals(ext);
    }

    public static CharSequence highlight(String text, String ext, boolean dark) {
        int cComment = dark ? Color.parseColor("#7F8C8D") : Color.parseColor("#6A737D");
        int cString = dark ? Color.parseColor("#A5D6A7") : Color.parseColor("#1B7F3B");
        int cKeyword = dark ? Color.parseColor("#FFAB70") : Color.parseColor("#B34700");
        int cAnno = dark ? Color.parseColor("#F9D56E") : Color.parseColor("#8A6D00");
        int cNumber = dark ? Color.parseColor("#82AAFF") : Color.parseColor("#1D5FD1");
        int cTag = dark ? Color.parseColor("#F78C6C") : Color.parseColor("#B8002E");
        int cAttr = dark ? Color.parseColor("#C792EA") : Color.parseColor("#6F42C1");

        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if ("xml".equals(ext) || "html".equals(ext)) {
            Matcher m = XML.matcher(text);
            while (m.find()) {
                if (m.group(1) != null) span(sb, m.start(1), m.end(1), cComment);
                else if (m.group(2) != null) span(sb, m.start(2), m.end(2), cString);
                else if (m.group(3) != null) span(sb, m.start(3), m.end(3), cTag);
                else if (m.group(4) != null) span(sb, m.start(4), m.end(4), cAttr);
                else if (m.group(5) != null) span(sb, m.start(5), m.end(5), cTag);
            }
        } else if ("json".equals(ext)) {
            Matcher m = JSON.matcher(text);
            while (m.find()) {
                if (m.group(1) != null) span(sb, m.start(1), m.end(1), cAttr);
                else if (m.group(2) != null) span(sb, m.start(2), m.end(2), cString);
                else if (m.group(3) != null) span(sb, m.start(3), m.end(3), cKeyword);
                else if (m.group(4) != null) span(sb, m.start(4), m.end(4), cNumber);
            }
        } else {
            Matcher m = JAVA.matcher(text);
            while (m.find()) {
                if (m.group(1) != null) span(sb, m.start(1), m.end(1), cComment);
                else if (m.group(2) != null) span(sb, m.start(2), m.end(2), cComment);
                else if (m.group(3) != null) span(sb, m.start(3), m.end(3), cString);
                else if (m.group(4) != null) span(sb, m.start(4), m.end(4), cString);
                else if (m.group(5) != null) span(sb, m.start(5), m.end(5), cKeyword);
                else if (m.group(6) != null) span(sb, m.start(6), m.end(6), cAnno);
                else if (m.group(7) != null) span(sb, m.start(7), m.end(7), cNumber);
            }
        }
        return sb;
    }

    private static void span(SpannableStringBuilder sb, int s, int e, int color) {
        sb.setSpan(new ForegroundColorSpan(color), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
