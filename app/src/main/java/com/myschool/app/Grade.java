package com.myschool.app;

import org.json.JSONObject;

public class Grade {
    private final String courseCode, courseTitle, grade, term, sortKey;

    public Grade(JSONObject json) {
        courseCode = json.optString("courseno", "---");
        courseTitle = json.optString("description", "");
        String g = json.optString("grade", "");
        grade = g.isEmpty() ? "\u2014" : g; // not graded yet
        String sy = json.optString("sy"), sem = json.optString("sem");
        term = sy.replace('-', '\u2013') + ", " + sem + " sem";
        sortKey = sy + sem; // "2026-20271st" sorts newest-last as text
    }

    public String getCourseCode() { return courseCode; }
    public String getCourseTitle() { return courseTitle; }
    public String getGrade() { return grade; }
    public String getTerm() { return term; }
    public String getSortKey() { return sortKey; }
}