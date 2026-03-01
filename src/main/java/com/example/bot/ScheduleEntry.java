package com.example.bot;

import java.time.LocalDateTime;

public class ScheduleEntry {
    private String subject;
    private LocalDateTime time;

    public ScheduleEntry(String subject, LocalDateTime time) {
        this.subject = subject;
        this.time = time;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getTime() {
        return time;
    }
}