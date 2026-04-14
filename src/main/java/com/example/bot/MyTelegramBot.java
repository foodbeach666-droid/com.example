package com.example.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class MyTelegramBot {

    private static List<ScheduleEntry> schedule = new ArrayList<>();
    private static final String FILE_NAME = "schedule.json";

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private static long pendingChatId = 0;
    private static String pendingSubject = null;
    private static String pendingStep = null;
    private static LocalDate pendingDate = null;

    private static void loadSchedule() {
        try (Reader reader = new FileReader(FILE_NAME)) {
            schedule = gson.fromJson(reader, new TypeToken<List<ScheduleEntry>>() {
            }.getType());
            if (schedule == null) schedule = new ArrayList<>();
        } catch (IOException e) {
            schedule = new ArrayList<>();
        }
    }

    private static void saveSchedule() {
        try (Writer writer = new FileWriter(FILE_NAME)) {
            gson.toJson(schedule, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String botToken = "8530517849:AAEpj8GF1gkbM69Wb3Cjd6GfiFhOZcZc4RA";

        TelegramBot bot = new TelegramBot(botToken);
        loadSchedule();

        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                if (update.message() != null && update.message().text() != null) {
                    long chatId = update.message().chat().id();
                    String messageText = update.message().text();

                    String response = handleMessage(bot, chatId, messageText);
                    bot.execute(new SendMessage(chatId, response));
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        System.out.println("Бот запущен");
    }

    private static String handleMessage(TelegramBot bot, long chatId, String message) {
        message = message.trim();

        if (pendingChatId == chatId && pendingSubject != null) {
            if (pendingStep.equals("waiting_full_datetime")) {
                try {
                    LocalDateTime time = parseDateTime(message);
                    schedule.add(new ScheduleEntry(pendingSubject, time));
                    saveSchedule();

                    String msg = "✅ Добавлено!\n" + pendingSubject + " → " +
                            time.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

                    resetState();
                    return msg;
                } catch (DateTimeParseException e) {
                    return "Не понял дату/время:\n" + message + "\n" +
                            "Пример:2026-02-25 14:30";
                }
            }

            if (pendingStep == null || pendingStep.equals("waiting_day")) {
                LocalDate date = LocalDate.now();

                if (message.equals("Сегодня")) {
                    // date остаётся
                } else if (message.equals("Завтра")) {
                    date = date.plusDays(1);
                } else if (message.equals("Послезавтра")) {
                    date = date.plusDays(2);
                } else if (message.equals("Ввести дату вручную")) {
                    pendingStep = "waiting_full_datetime";
                    return "Введи дату и время\nПример:\n2026-02-25 14:30";
                } else {
                    return "Выбери кнопку или «Ввести дату вручную»";
                }

                pendingDate = date;
                pendingStep = "waiting_time";

                return "День: " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                        "\nВведи время (Пример: 14:30)";
            }
            if (pendingStep.equals("waiting_time")) {
                try {
                    LocalTime time = LocalTime.parse(message.trim(), DateTimeFormatter.ofPattern("H:mm"));
                    LocalDateTime full = LocalDateTime.of(pendingDate, time);

                    schedule.add(new ScheduleEntry(pendingSubject, full));
                    saveSchedule();

                    String msg = "✅ Добавлено!\n" + pendingSubject + " → " +
                            full.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

                    resetState();
                    return msg;
                } catch (Exception e) {
                    return "Не понял время. Формат: 14:30 или 9:00";
                }
            }
        }
    if (message.startsWith("/create")) {
            String subject = message.substring(7).trim();
            if (subject.isEmpty()) {
                return "Напиши: /create Название пары/дисциплины";
            }
            pendingChatId = chatId;
            pendingSubject = subject;
            pendingStep = "waiting_day";
            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup(
                    new KeyboardButton("Сегодня"),
                    new KeyboardButton("Завтра"),
                    new KeyboardButton("Послезавтра"),
                    new KeyboardButton("Ввести дату вручную")
            ).resizeKeyboard(true).oneTimeKeyboard(true);

            bot.execute(new SendMessage(chatId, "Когда пара по \"" + subject + "\"?")
                    .replyMarkup(keyboard));

            return "Выбери день ↓";
        }
        if (message.equals("/track")) {
            if (schedule.isEmpty()) return "Расписание пустое.";
            StringBuilder sb = new StringBuilder("Расписание:\n");
            for (ScheduleEntry entry : schedule) {
                sb.append(entry.getSubject())
                        .append(" — ")
                        .append(entry.getTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }
            return sb.toString();
        }
        if (message.equals("/clear")) {
            schedule.clear();
            saveSchedule();
            return "Расписание очищено.";
        }

        return "Команды:\n" +
                "/create [предмет] — добавить\n" +
                "/track — посмотреть\n" +
                "/clear — очистить\n";
    }

    private static void resetState() {
        pendingChatId = 0;
        pendingSubject = null;
        pendingStep = null;
        pendingDate = null;
    }
    private static LocalDateTime parseDateTime(String input) {
        input = input.trim().toLowerCase()
                .replace("т", "t")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*:\\s*", ":");

        LocalDate baseDate = LocalDate.now();
        String timeStr = null;

        if (input.startsWith("завтра")) {
            timeStr = input.substring(6).trim();
            baseDate = baseDate.plusDays(1);
        } else if (input.startsWith("послезавтра")) {
            timeStr = input.substring(11).trim();
            baseDate = baseDate.plusDays(2);
        }

        if (timeStr != null && !timeStr.isEmpty()) {
            try {
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("H:mm"));
                return LocalDateTime.of(baseDate, time);
            } catch (Exception ignored) {
            }
        }

        String[] ruMonths = {"января", "февраля", "марта", "апреля", "мая", "июня",
                "июля", "августа", "сентября", "октября", "ноября", "декабря"};

        for (int i = 0; i < ruMonths.length; i++) {
            if (input.contains(ruMonths[i])) {
                String[] parts = input.split(ruMonths[i]);
                if (parts.length == 2) {
                    String dayStr = parts[0].trim();
                    String timeStr2 = parts[1].trim();
                    try {
                        int day = Integer.parseInt(dayStr);
                        int month = i + 1;
                        LocalTime time = LocalTime.parse(timeStr2, DateTimeFormatter.ofPattern("H:mm"));
                        LocalDate date = LocalDate.of(LocalDate.now().getYear(), month, day);
                        return LocalDateTime.of(date, time);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        try {
            String[] parts = input.split(" ");
            if (parts.length == 2) {
                String datePart = parts[0];
                String timeStr3 = parts[1];
                LocalDate d = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("dd.MM"));
                LocalTime t = LocalTime.parse(timeStr3, DateTimeFormatter.ofPattern("H:mm"));
                return LocalDateTime.of(d.withYear(LocalDate.now().getYear()), t);
            }
        } catch (Exception ignored) {
        }

        try {
            String[] parts = input.split(" ");
            if (parts.length == 2) {
                String datePart = parts[0];
                String timeStr4 = parts[1];
                LocalDate d = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                LocalTime t = LocalTime.parse(timeStr4, DateTimeFormatter.ofPattern("H:mm"));
                return LocalDateTime.of(d, t);
            }
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }

        throw new DateTimeParseException("Не распознан формат: '" + input + "'", input, 0);
    }
}