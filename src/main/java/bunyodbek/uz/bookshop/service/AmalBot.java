package bunyodbek.uz.bookshop.service;

import bunyodbek.uz.bookshop.model.Book;
import bunyodbek.uz.bookshop.model.User;
import bunyodbek.uz.bookshop.repository.BookRepository;
import bunyodbek.uz.bookshop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AmalBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    @Value("${bot.name}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        // 1. Matnli xabar kelganda
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            // Foydalanuvchini tekshirish
            User user = userRepository.findByChatId(chatId).orElse(null);

            // 3.1 Botni ishga tushirish (/start) [cite: 17, 18]
            if (message.hasText() && message.getText().equals("/start")) {
                if (user == null || user.getPhoneNumber() == null) {
                    askForPhoneNumber(chatId); // Telefon raqam so'rash
                } else {
                    showMainMenu(chatId); // Asosiy menyu
                }
            }
            // Telefon raqam qabul qilish [cite: 19, 20, 22]
            else if (message.hasContact()) {
                saveUserPhone(chatId, message.getContact());
                showMainMenu(chatId);
            }
            // Menyular logikasi [cite: 24-28]
            else if (message.hasText()) {
                switch (message.getText()) {
                    case "📚 Mahsulotlar" -> showBooks(chatId); // [cite: 25, 30]
                    case "🛒 Savat" -> showCart(chatId); // [cite: 27, 38]
                    case "ℹ️ Bot haqida" -> sendInfo(chatId); // [cite: 26, 55]
                    case "🤝 Hamkorlik" -> sendCollaboration(chatId); // [cite: 28, 59]
                }
            }
        }
        // 2. Tugmalar bosilganda (CallbackQuery) - Masalan "Savatga qo'shish"
        else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    // Telefon raqam so'rash funksiyasi [cite: 19, 20]
    private void askForPhoneNumber(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Assalomu alaykum! Iltimos, telefon raqamingizni yuboring.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton("📞 Telefon raqamni yuborish");
        button.setRequestContact(true); // Contact button [cite: 20]
        row.add(button);

        keyboardMarkup.setKeyboard(Collections.singletonList(row));
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    // Asosiy menyu funksiyasi [cite: 23, 24]
    private void showMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Asosiy menyu:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📚 Mahsulotlar"); // [cite: 25]
        row1.add("🛒 Savat");       // [cite: 27]

        KeyboardRow row2 = new KeyboardRow();
        row2.add("ℹ️ Bot haqida");  // [cite: 26]
        row2.add("🤝 Hamkorlik");   // [cite: 28]

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    // Kitoblarni ko'rsatish funksiyasi [cite: 30, 31]
    private void showBooks(Long chatId) {
        List<Book> books = bookRepository.findAll(); // DB dan olish
        for (Book book : books) {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId);
            photo.setPhoto(new InputFile(book.getImageUrl())); // [cite: 34]
            photo.setCaption(String.format("%s\nNarxi: %.2f so'm", book.getName(), book.getPrice())); // [cite: 33, 35]

            // Savatga qo'shish tugmasi (Inline) [cite: 37]
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("➕ Savatga qo‘shish");
            btn.setCallbackData("add_cart_" + book.getId());

            row.add(btn);
            rows.add(row);
            markup.setKeyboard(rows);
            photo.setReplyMarkup(markup);

            try { execute(photo); } catch (TelegramApiException e) { e.printStackTrace(); }
        }
    }

    private void executeMessage(SendMessage message) {
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    // Foydalanuvchi telefonini saqlash [cite: 22]
    private void saveUserPhone(Long chatId, Contact contact) {
        User user = new User();
        user.setChatId(chatId);
        user.setPhoneNumber(contact.getPhoneNumber());
        userRepository.save(user);
    }

    // Callbacklarni ishlash (Savatga qo'shish)
    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (data.startsWith("add_cart_")) {
            // Savatga qo'shish logikasi [cite: 37, 40]
            // Bu yerda CartItemRepository orqali saqlash kerak bo'ladi
        }
    }

    // Hamkorlik bo'limi [cite: 59, 60]
    private void sendCollaboration(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Biz bilan bog'lanish: +998 90 123 45 67");
        executeMessage(msg);
    }

    // Bot haqida [cite: 55, 57]
    private void sendInfo(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Bot nomi: Amal\nMaqsadi: Kitob savdosi");
        executeMessage(msg);
    }
}
