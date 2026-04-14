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
import java.util.stream.Collectors;

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
    private static int removePastEntries() {
        LocalDateTime now = LocalDateTime.now();
        int oldSize = schedule.size();
        schedule = schedule.stream()
                .filter(entry -> entry.getTime().isAfter(now))
                .collect(Collectors.toList());
        int removedCount = oldSize - schedule.size();
        if (removedCount > 0) {
            saveSchedule();
        }
        return removedCount;
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

        if (pendingChatId == chatId && "waiting_remove".equals(pendingStep)) {
            try {
                int index = Integer.parseInt(message) - 1;
                if (index >= 0 && index < schedule.size()) {
                    ScheduleEntry removed = schedule.remove(index);
                    saveSchedule();
                    resetState();
                    return "🗑️ Удалено: " + removed.getSubject() + " — " +
                            removed.getTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                } else {
                    return "❌ Неверный номер. Введи число от 1 до " + schedule.size();
                }
            } catch (NumberFormatException e) {
                return "❌ Введи число — номер пары из списка.\nИли отправь /cancel для отмены.";
            }
        }

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
                            "Пример:\n2026-02-25 14:30\n25 февраля 14:30" +
                            "\n25.02.2026 14:30";
                }
            }

            if (pendingStep == null || pendingStep.equals("waiting_day")) {
                LocalDate date = LocalDate.now();

                if (message.equals("Сегодня")) {
                } else if (message.equals("Завтра")) {
                    date = date.plusDays(1);
                } else if (message.equals("Послезавтра")) {
                    date = date.plusDays(2);
                } else if (message.equals("Ввести дату вручную")) {
                    pendingStep = "waiting_full_datetime";
                    return "Введи дату и время\nПример:\n2026-02-25 14:30" +
                            "\n25 февраля 14:30" +
                            "\n25.02.2026 14:30";
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

        if (message.equals("/cancel") && pendingChatId == chatId && pendingSubject != null) {
            resetState();
            return "Добавление отменено.";
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
            int removed = removePastEntries();

            if (schedule.isEmpty()) {
                if (removed > 0) {
                    return "🗑️ Удалено " + removed + " прошедших пар.\n📭 Расписание пустое.";
                }
                return "📭 Расписание пустое.";
            }

            StringBuilder sb = new StringBuilder();
            if (removed > 0) {
                sb.append("🗑️ Автоматически удалено ").append(removed).append(" прошедших пар.\n\n");
            }
            sb.append("📅 Текущее расписание:\n");
            for (int i = 0; i < schedule.size(); i++) {
                ScheduleEntry entry = schedule.get(i);
                sb.append(i + 1).append(". ")
                        .append(entry.getSubject())
                        .append(" — ")
                        .append(entry.getTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }
            return sb.toString();
        }

        if (message.equals("/remove")) {
            int removed = removePastEntries();

            if (schedule.isEmpty()) {
                if (removed > 0) {
                    return "🗑️ Удалено " + removed + " прошедших пар.\n📭 Расписание пустое, нечего удалять.";
                }
                return "📭 Расписание пустое, нечего удалять.";
            }

            StringBuilder sb = new StringBuilder("🗑️ Введи номер пары для удаления:\n\n");
            for (int i = 0; i < schedule.size(); i++) {
                ScheduleEntry entry = schedule.get(i);
                sb.append(i + 1).append(". ")
                        .append(entry.getSubject())
                        .append(" — ")
                        .append(entry.getTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))
                        .append("\n");
            }
            sb.append("\nЧтобы отменить, отправь /cancel");

            pendingChatId = chatId;
            pendingStep = "waiting_remove";
            return sb.toString();
        }

        if (message.equals("/clear")) {
            schedule.clear();
            saveSchedule();
            resetState();
            return "🧹 Расписание полностью очищено.";
        }

        if (message.equals("/help")) {
            return "📚 Доступные команды:\n\n" +
                    "/create [предмет] — добавить новую пару\n" +
                    "/track — показать расписание (старые пары удаляются автоматически)\n" +
                    "/remove — удалить одну пару по номеру\n" +
                    "/clear — удалить ВСЕ пары\n" +
                    "/cancel — отменить текущее действие\n" +
                    "/help — показать эту справку";
        }

        if (message.equals("/start")) {
            return "🎓 Привет! Я бот для отслеживания расписания пар.\n\n" +
                    "Отправь /help, чтобы увидеть список команд.";
        }

        return "❓ Неизвестная команда.\nОтправь /help для списка команд.";
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