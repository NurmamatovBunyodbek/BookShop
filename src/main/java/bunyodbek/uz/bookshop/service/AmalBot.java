package bunyodbek.uz.bookshop.service;

import bunyodbek.uz.bookshop.model.Book;
import bunyodbek.uz.bookshop.model.CartItem;
import bunyodbek.uz.bookshop.model.User;
import bunyodbek.uz.bookshop.repository.BookRepository;
import bunyodbek.uz.bookshop.repository.CartItemRepository;
import bunyodbek.uz.bookshop.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
import java.util.Optional;

@Component
public class AmalBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CartItemRepository cartItemRepository;

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

        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            User user = userRepository.findByChatId(chatId).orElse(null);

            if (message.hasText() && message.getText().equals("/start")) {
                if (user == null || user.getPhoneNumber() == null) {
                    askForPhoneNumber(chatId);
                } else {
                    showMainMenu(chatId);
                }
            }
            else if (message.hasContact()) {
                saveUserPhone(chatId, message.getContact());
                showMainMenu(chatId);
            }
            else if (message.hasText()) {
                switch (message.getText()) {
                    case "📚 Mahsulotlar" -> showBooks(chatId);
                    case "🛒 Savat" -> showCart(chatId);
                    case "ℹ️ Bot haqida" -> sendInfo(chatId);
                    case "🤝 Hamkorlik" -> sendCollaboration(chatId);
                }
            }
        }
        else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }


    private void askForPhoneNumber(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Assalomu alaykum! Iltimos, telefon raqamingizni yuboring.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        KeyboardButton button = new KeyboardButton("📞 Telefon raqamni yuborish");
        button.setRequestContact(true);
        row.add(button);

        keyboardMarkup.setKeyboard(Collections.singletonList(row));
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }


    private void showMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Asosiy menyu:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📚 Mahsulotlar");
        row1.add("🛒 Savat");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("ℹ️ Bot haqida");
        row2.add("🤝 Hamkorlik");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }


    private void showBooks(Long chatId) {
        List<Book> books = bookRepository.findAll(); // DB dan olish
        for (Book book : books) {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId);
            photo.setPhoto(new InputFile(book.getImageUrl()));
            photo.setCaption(String.format("%s\nNarxi: %.2f so'm", book.getName(), book.getPrice()));


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


    private void saveUserPhone(Long chatId, Contact contact) {
        User user = userRepository.findByChatId(chatId).orElse(new User());
        user.setChatId(chatId);
        user.setPhoneNumber(contact.getPhoneNumber());
        userRepository.save(user);
    }


    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        User user = userRepository.findByChatId(chatId).orElse(null);

        if (user == null) {
            SendMessage message = new SendMessage(chatId.toString(), "Iltimos, avval /start buyrug'ini bosing va telefon raqamingizni yuboring.");
            executeMessage(message);
            return;
        }

        if (data.startsWith("add_cart_")) {
            Long bookId = Long.parseLong(data.substring("add_cart_".length()));
            Optional<Book> bookOptional = bookRepository.findById(bookId);

            if (bookOptional.isPresent()) {
                Book book = bookOptional.get();
                CartItem cartItem = cartItemRepository.findByUserAndBook(user, book)
                        .orElse(new CartItem());
                cartItem.setUser(user);
                cartItem.setBook(book);
                cartItem.setQuantity(cartItem.getQuantity() != null ? cartItem.getQuantity() + 1 : 1);
                cartItemRepository.save(cartItem);

                SendMessage message = new SendMessage(chatId.toString(), String.format("'%s' savatga qo'shildi. Miqdori: %d", book.getName(), cartItem.getQuantity()));
                executeMessage(message);
            } else {
                SendMessage message = new SendMessage(chatId.toString(), "Kitob topilmadi.");
                executeMessage(message);
            }
        }
    }


    private void showCart(Long chatId) {
        User user = userRepository.findByChatId(chatId).orElse(null);
        if (user == null) {
            SendMessage message = new SendMessage(chatId.toString(), "Iltimos, avval /start buyrug'ini bosing va telefon raqamingizni yuboring.");
            executeMessage(message);
            return;
        }

        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        if (cartItems.isEmpty()) {
            SendMessage message = new SendMessage(chatId.toString(), "Savatingiz bo'sh.");
            executeMessage(message);
            return;
        }

        StringBuilder cartMessage = new StringBuilder("Savatingizdagi mahsulotlar:\n\n");
        double totalAmount = 0.0;

        for (CartItem item : cartItems) {
            cartMessage.append(String.format("%s x %d = %.2f so'm\n",
                    item.getBook().getName(),
                    item.getQuantity(),
                    item.getBook().getPrice() * item.getQuantity()));
            totalAmount += item.getBook().getPrice() * item.getQuantity();
        }
        cartMessage.append(String.format("\nJami: %.2f so'm", totalAmount));

        SendMessage message = new SendMessage(chatId.toString(), cartMessage.toString());
        executeMessage(message);
    }


    private void sendCollaboration(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Biz bilan bog'lanish: +998 90 123 45 67");
        executeMessage(msg);
    }

    private void sendInfo(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Bot nomi: Amal\nMaqsadi: Kitob savdosi");
        executeMessage(msg);
    }
}
