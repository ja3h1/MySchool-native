package com.myschool.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class GradesAdapter extends RecyclerView.Adapter<GradesAdapter.GradeViewHolder> {
    private final List<Grade> grades;
    private final boolean showTerm; // "All semesters" view mixes terms

    public GradesAdapter(List<Grade> grades, boolean showTerm) {
        this.grades = grades;
        this.showTerm = showTerm;
    }

    @NonNull
    @Override
    public GradeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grade, parent, false);
        return new GradeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GradeViewHolder holder, int position) {
        Grade grade = grades.get(position);
        holder.tvCourseCode.setText(showTerm ? grade.getCourseCode() + ", " + grade.getTerm() : grade.getCourseCode());
        holder.tvCourseTitle.setText(grade.getCourseTitle());
        holder.tvGrade.setText(grade.getGrade());
    }

    @Override
    public int getItemCount() {
        return grades.size();
    }

    static class GradeViewHolder extends RecyclerView.ViewHolder {
        TextView tvCourseCode, tvCourseTitle, tvGrade;

        public GradeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCourseCode = itemView.findViewById(R.id.tvCourseCode);
            tvCourseTitle = itemView.findViewById(R.id.tvCourseTitle);
            tvGrade = itemView.findViewById(R.id.tvGrade);
        }
    }
}