package bunyodbek.uz.bookshop.service;

import bunyodbek.uz.bookshop.model.*;
import bunyodbek.uz.bookshop.model.User;
import bunyodbek.uz.bookshop.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.LocalDateTime;
import java.util.*;

@Component
public class AmalBot extends TelegramLongPollingBot {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Value("${bot.name}")
    private String botUsername;

    @Value("${bot.token}")
    private String botToken;

    @Value("${bot.admin.chatIds}")
    private String adminChatIdsStr;

    private final List<Long> adminChatIds = new ArrayList<>();

    // Admin state management
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Book> tempBooks = new HashMap<>();
    private final Map<Long, Long> pendingOrderIds = new HashMap<>();

    @PostConstruct
    public void init() {
        if (adminChatIdsStr != null && !adminChatIdsStr.isEmpty()) {
            for (String id : adminChatIdsStr.split(",")) {
                try {
                    adminChatIds.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException e) {
                    System.err.println("Xato admin ID: " + id);
                }
            }
        }
    }

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
                    if ("ADMIN".equals(user.getRole())) {
                        showAdminMenu(chatId);
                    } else {
                        showMainMenu(chatId);
                    }
                }
            }
            else if (message.hasContact()) {
                saveUserPhone(chatId, message.getContact(), message.getFrom());
                User updatedUser = userRepository.findByChatId(chatId).orElse(null);
                if (updatedUser != null && "ADMIN".equals(updatedUser.getRole())) {
                    showAdminMenu(chatId);
                } else {
                    showMainMenu(chatId);
                }
            }
            else if (message.hasText()) {
                String text = message.getText();
                
                if (user != null && "ADMIN".equals(user.getRole())) {
                    if (userStates.containsKey(chatId)) {
                        handleAdminInput(chatId, text);
                        return;
                    }
                    
                    switch (text) {
                        case "➕ Kitob qo'shish" -> startAddBook(chatId);
                        case "✏️ Kitob tahrirlash" -> showBooksForEdit(chatId);
                        case "🗑 Kitob o'chirish" -> showBooksForDelete(chatId);
                        case "📊 Statistika" -> showStatistics(chatId);
                        case "⬅️ Chiqish" -> showMainMenu(chatId);
                        default -> showAdminMenu(chatId);
                    }
                } else {
                    if (userStates.containsKey(chatId) && "WAITING_FOR_ADDRESS".equals(userStates.get(chatId))) {
                        handleUserAddressInput(chatId, text);
                        return;
                    }

                    switch (text) {
                        case "📚 Mahsulotlar" -> showCategories(chatId);
                        case "🛒 Savat" -> showCart(chatId);
                        case "📦 Sotib olinganlar" -> showPurchasedBooks(chatId);
                        case "ℹ️ Bot haqida" -> sendInfo(chatId);
                        case "🤝 Hamkorlik" -> sendCollaboration(chatId);
                        case "⬅️ Orqaga" -> showMainMenu(chatId);
                        default -> {
                            // Check if text is a category
                            if (isCategory(text)) {
                                showBooksByCategory(chatId, text);
                            } else {
                                showMainMenu(chatId);
                            }
                        }
                    }
                }
            }
        }
        else if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    private boolean isCategory(String text) {
        return List.of("Badiiy kitoblar", "Diniy kitoblar", "Diniy to'plamlar", "Ertak kitoblar", "Romanlar").contains(text);
    }

    // --- Admin Methods ---

    private void showAdminMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Admin menyusi:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("➕ Kitob qo'shish");
        row1.add("✏️ Kitob tahrirlash");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🗑 Kitob o'chirish");
        row2.add("📊 Statistika");
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add("⬅️ Chiqish");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void startAddBook(Long chatId) {
        userStates.put(chatId, "ADD_BOOK_NAME");
        tempBooks.put(chatId, new Book());
        SendMessage msg = new SendMessage(chatId.toString(), "Kitob nomini kiriting:");
        executeMessage(msg);
    }

    private void handleAdminInput(Long chatId, String text) {
        String state = userStates.get(chatId);
        Book tempBook = tempBooks.get(chatId);

        switch (state) {
            case "ADD_BOOK_NAME" -> {
                tempBook.setName(text);
                userStates.put(chatId, "ADD_BOOK_PRICE");
                SendMessage msg = new SendMessage(chatId.toString(), "Kitob narxini kiriting (faqat raqam):");
                executeMessage(msg);
            }
            case "ADD_BOOK_PRICE" -> {
                try {
                    double price = Double.parseDouble(text);
                    tempBook.setPrice(price);
                    userStates.put(chatId, "ADD_BOOK_IMAGE");
                    SendMessage msg = new SendMessage(chatId.toString(), "Kitob rasmi URL manzilini kiriting:");
                    executeMessage(msg);
                } catch (NumberFormatException e) {
                    SendMessage msg = new SendMessage(chatId.toString(), "Iltimos, to'g'ri narx kiriting:");
                    executeMessage(msg);
                }
            }
            case "ADD_BOOK_IMAGE" -> {
                tempBook.setImageUrl(text);
                userStates.put(chatId, "ADD_BOOK_CATEGORY");
                
                SendMessage msg = new SendMessage();
                msg.setChatId(chatId);
                msg.setText("Kitob kategoriyasini tanlang:");
                
                ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
                keyboardMarkup.setResizeKeyboard(true);
                keyboardMarkup.setOneTimeKeyboard(true);
                List<KeyboardRow> keyboard = new ArrayList<>();
                
                KeyboardRow row1 = new KeyboardRow();
                row1.add("Badiiy kitoblar");
                row1.add("Diniy kitoblar");
                
                KeyboardRow row2 = new KeyboardRow();
                row2.add("Diniy to'plamlar");
                row2.add("Ertak kitoblar");
                
                KeyboardRow row3 = new KeyboardRow();
                row3.add("Romanlar");
                
                keyboard.add(row1);
                keyboard.add(row2);
                keyboard.add(row3);
                keyboardMarkup.setKeyboard(keyboard);
                msg.setReplyMarkup(keyboardMarkup);
                
                executeMessage(msg);
            }
            case "ADD_BOOK_CATEGORY" -> {
                if (isCategory(text)) {
                    tempBook.setCategory(text);
                    tempBook.setDeleted(false);
                    bookRepository.save(tempBook);
                    userStates.remove(chatId);
                    tempBooks.remove(chatId);
                    SendMessage msg = new SendMessage(chatId.toString(), "Kitob muvaffaqiyatli qo'shildi!");
                    executeMessage(msg);
                    showAdminMenu(chatId);
                } else {
                    SendMessage msg = new SendMessage(chatId.toString(), "Iltimos, quyidagi kategoriyalardan birini tanlang.");
                    executeMessage(msg);
                }
            }
            case "EDIT_BOOK_PRICE" -> {
                 try {
                    double price = Double.parseDouble(text);
                    tempBook.setPrice(price);
                    bookRepository.save(tempBook);
                    userStates.remove(chatId);
                    tempBooks.remove(chatId);
                    SendMessage msg = new SendMessage(chatId.toString(), "Kitob narxi yangilandi!");
                    executeMessage(msg);
                    showAdminMenu(chatId);
                 } catch (NumberFormatException e) {
                     SendMessage msg = new SendMessage(chatId.toString(), "Iltimos, to'g'ri narx kiriting:");
                     executeMessage(msg);
                 }
            }
        }
    }

    private void showBooksForDelete(Long chatId) {
        List<Book> books = bookRepository.findByDeletedFalse();
        if (books.isEmpty()) {
            executeMessage(new SendMessage(chatId.toString(), "Kitoblar yo'q."));
            return;
        }
        
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("O'chirish uchun kitobni tanlang:");
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (Book book : books) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("❌ " + book.getName());
            btn.setCallbackData("delete_book_" + book.getId());
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        executeMessage(msg);
    }
    
    private void showBooksForEdit(Long chatId) {
        List<Book> books = bookRepository.findByDeletedFalse();
        if (books.isEmpty()) {
            executeMessage(new SendMessage(chatId.toString(), "Kitoblar yo'q."));
            return;
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("Tahrirlash uchun kitobni tanlang (Narxini o'zgartirish):");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Book book : books) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("✏️ " + book.getName());
            btn.setCallbackData("edit_book_" + book.getId());
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        executeMessage(msg);
    }

    private void showStatistics(Long chatId) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText("Statistika davrini tanlang:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton dailyBtn = new InlineKeyboardButton();
        dailyBtn.setText("📅 Kunlik");
        dailyBtn.setCallbackData("stats_daily");

        InlineKeyboardButton weeklyBtn = new InlineKeyboardButton();
        weeklyBtn.setText("📅 Haftalik");
        weeklyBtn.setCallbackData("stats_weekly");

        InlineKeyboardButton monthlyBtn = new InlineKeyboardButton();
        monthlyBtn.setText("📅 Oylik");
        monthlyBtn.setCallbackData("stats_monthly");

        row.add(dailyBtn);
        row.add(weeklyBtn);
        row.add(monthlyBtn);
        rows.add(row);

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        executeMessage(msg);
    }

    // --- User Methods ---

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
        row2.add("📦 Sotib olinganlar");
        row2.add("ℹ️ Bot haqida");
        
        KeyboardRow row3 = new KeyboardRow();
        row3.add("🤝 Hamkorlik");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void showCategories(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Kategoriyani tanlang:");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("Badiiy kitoblar");
        row1.add("Diniy kitoblar");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("Diniy to'plamlar");
        row2.add("Ertak kitoblar");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("Romanlar");
        
        KeyboardRow row4 = new KeyboardRow();
        row4.add("⬅️ Orqaga");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);
        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        executeMessage(message);
    }

    private void showBooksByCategory(Long chatId, String category) {
        List<Book> books = bookRepository.findByCategoryAndDeletedFalse(category);
        if (books.isEmpty()) {
             SendMessage message = new SendMessage(chatId.toString(), "Bu kategoriyada kitoblar yo'q.");
             executeMessage(message);
             return;
        }
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

            try {
                execute(photo);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void executeMessage(SendMessage message) {
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }


    private void saveUserPhone(Long chatId, Contact contact, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        User user = userRepository.findByChatId(chatId).orElse(new User());
        user.setChatId(chatId);
        user.setPhoneNumber(contact.getPhoneNumber());
        
        user.setFirstName(telegramUser.getFirstName());
        user.setLastName(telegramUser.getLastName());
        user.setUsername(telegramUser.getUserName());

        if (adminChatIds.contains(chatId)) {
            user.setRole("ADMIN");
        } else if (user.getRole() == null) {
            user.setRole("USER");
        }
        userRepository.save(user);
    }


    @Transactional
    protected void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        User user = userRepository.findByChatId(chatId).orElse(null);

        if (user == null) {
            SendMessage message = new SendMessage(chatId.toString(), "Iltimos, avval /start buyrug'ini bosing va telefon raqamingizni yuboring.");
            executeMessage(message);
            return;
        }
        
        // Admin callbacks
        if ("ADMIN".equals(user.getRole())) {
            if (data.startsWith("delete_book_")) {
                Long bookId = Long.parseLong(data.substring("delete_book_".length()));
                
                Optional<Book> bookOpt = bookRepository.findById(bookId);
                if (bookOpt.isPresent()) {
                    Book book = bookOpt.get();
                    book.setDeleted(true);
                    bookRepository.save(book);
                    
                    // Also remove from carts to avoid confusion
                    cartItemRepository.deleteByBookId(bookId);
                    
                    SendMessage message = new SendMessage(chatId.toString(), "Kitob o'chirildi.");
                    executeMessage(message);
                    showAdminMenu(chatId);
                } else {
                    SendMessage message = new SendMessage(chatId.toString(), "Kitob topilmadi.");
                    executeMessage(message);
                }
                return;
            } else if (data.startsWith("edit_book_")) {
                Long bookId = Long.parseLong(data.substring("edit_book_".length()));
                Optional<Book> bookOpt = bookRepository.findById(bookId);
                if (bookOpt.isPresent()) {
                    tempBooks.put(chatId, bookOpt.get());
                    userStates.put(chatId, "EDIT_BOOK_PRICE");
                    SendMessage message = new SendMessage(chatId.toString(), "Yangi narxni kiriting:");
                    executeMessage(message);
                }
                return;
            } else if (data.startsWith("stats_")) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start = end;
                String periodName = "";

                if (data.equals("stats_daily")) {
                    start = end.minusDays(1);
                    periodName = "Kunlik";
                } else if (data.equals("stats_weekly")) {
                    start = end.minusWeeks(1);
                    periodName = "Haftalik";
                } else if (data.equals("stats_monthly")) {
                    start = end.minusMonths(1);
                    periodName = "Oylik";
                }

                List<Order> orders = orderRepository.findByStatusAndOrderDateBetween("CONFIRMED", start, end);
                
                double totalRevenue = 0;
                Set<Long> uniqueBuyers = new HashSet<>();
                int totalBooksSold = 0;

                for (Order order : orders) {
                    totalRevenue += order.getTotalAmount();
                    uniqueBuyers.add(order.getUser().getId());
                    for (OrderItem item : order.getOrderItems()) {
                        totalBooksSold += item.getQuantity();
                    }
                }

                String report = String.format(
                    "📊 %s statistika:\n\n" +
                    "💰 Jami tushum: %.2f so'm\n" +
                    "👥 Xaridorlar soni: %d ta\n" +
                    "📚 Sotilgan kitoblar: %d ta\n" +
                    "🧾 Jami buyurtmalar: %d ta",
                    periodName, totalRevenue, uniqueBuyers.size(), totalBooksSold, orders.size()
                );

                SendMessage message = new SendMessage(chatId.toString(), report);
                executeMessage(message);
                return;
            }
        }

        if (data.startsWith("add_cart_")) {
            Long bookId = Long.parseLong(data.substring("add_cart_".length()));
            Optional<Book> bookOptional = bookRepository.findById(bookId);

            if (bookOptional.isPresent() && (bookOptional.get().getDeleted() == null || !bookOptional.get().getDeleted())) {
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
                SendMessage message = new SendMessage(chatId.toString(), "Kitob topilmadi yoki sotuvda yo'q.");
                executeMessage(message);
            }
        }
        else if (data.equals("checkout")) {
            List<CartItem> cartItems = cartItemRepository.findByUser(user);
            if (cartItems.isEmpty()) {
                executeMessage(new SendMessage(chatId.toString(), "Savatingiz bo'sh."));
                return;
            }

            double totalAmount = cartItems.stream()
                    .mapToDouble(item -> item.getBook().getPrice() * item.getQuantity())
                    .sum();

            Order order = new Order();
            order.setUser(user);
            order.setOrderDate(LocalDateTime.now());
            order.setStatus("PENDING");
            order.setTotalAmount(totalAmount);
            
            List<OrderItem> orderItems = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                OrderItem orderItem = new OrderItem();
                orderItem.setBook(cartItem.getBook());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setPrice(cartItem.getBook().getPrice());
                orderItems.add(orderItem);
            }
            order.setOrderItems(orderItems);
            Order savedOrder = orderRepository.save(order);

            // Ask for address
            userStates.put(chatId, "WAITING_FOR_ADDRESS");
            pendingOrderIds.put(chatId, savedOrder.getId());
            
            SendMessage msg = new SendMessage(chatId.toString(), "Iltimos, yetkazib berish manzilini kiriting (Masalan: Yangi hayot tuman Navro'z ko'chasi 56):");
            executeMessage(msg);
        }
        else if (data.startsWith("confirm_order_")) {
            Long orderId = Long.parseLong(data.substring("confirm_order_".length()));
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                if ("CONFIRMED".equals(order.getStatus())) {
                    executeMessage(new SendMessage(chatId.toString(), "Bu buyurtma allaqachon tasdiqlangan."));
                    return;
                }
                
                order.setStatus("CONFIRMED");
                orderRepository.save(order);
                
                // Clear cart
                List<CartItem> cartItems = cartItemRepository.findByUser(user);
                cartItemRepository.deleteAll(cartItems);
                
                executeMessage(new SendMessage(chatId.toString(), "Buyurtmangiz qabul qilindi! Tez orada operatorlarimiz siz bilan bog'lanishadi."));
                
                // Notify Admins
                User customer = order.getUser();
                
                StringBuilder booksInfo = new StringBuilder();
                for (OrderItem item : order.getOrderItems()) {
                    booksInfo.append(String.format("- %s x %d\n", item.getBook().getName(), item.getQuantity()));
                }

                String adminMsgText = String.format(
                    "🔔 Yangi buyurtma!\n\n" +
                    "👤 Foydalanuvchi: %s %s\n" +
                    "📞 Tel: %s\n" +
                    "🔗 Username: @%s\n" +
                    "💰 Summa: %.2f so'm\n" +
                    "🆔 Buyurtma ID: %d\n" +
                    "📍 Manzil: %s\n\n" +
                    "📚 Kitoblar:\n%s",
                    customer.getFirstName(), 
                    customer.getLastName() != null ? customer.getLastName() : "",
                    customer.getPhoneNumber(),
                    customer.getUsername() != null ? customer.getUsername() : "mavjud emas",
                    order.getTotalAmount(),
                    order.getId(),
                    order.getAddress() != null ? order.getAddress() : "Kiritilmagan",
                    booksInfo.toString()
                );
                
                for (Long adminId : adminChatIds) {
                    SendMessage adminMsg = new SendMessage(adminId.toString(), adminMsgText);
                    executeMessage(adminMsg);
                }

            } else {
                executeMessage(new SendMessage(chatId.toString(), "Buyurtma topilmadi."));
            }
        }
    }

    private void handleUserAddressInput(Long chatId, String address) {
        Long orderId = pendingOrderIds.get(chatId);
        if (orderId == null) {
            userStates.remove(chatId);
            showMainMenu(chatId);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setAddress(address);
            orderRepository.save(order);
            
            userStates.remove(chatId);
            pendingOrderIds.remove(chatId);

            SendMessage msg = new SendMessage(chatId.toString(), 
                String.format("Buyurtma ma'lumotlari:\n\nManzil: %s\nJami summa: %.2f so'm.\n\nBuyurtmani tasdiqlaysizmi?", 
                order.getAddress(), order.getTotalAmount()));
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("✅ Buyurtmani tasdiqlash");
            btn.setCallbackData("confirm_order_" + order.getId());
            row.add(btn);
            rows.add(row);
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            
            executeMessage(msg);
        } else {
            SendMessage msg = new SendMessage(chatId.toString(), "Xatolik yuz berdi. Buyurtma topilmadi.");
            executeMessage(msg);
            userStates.remove(chatId);
            pendingOrderIds.remove(chatId);
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
        
        // Add Checkout button
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText("💳 Sotib olish");
        btn.setCallbackData("checkout");
        row.add(btn);
        rows.add(row);
        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);
        
        executeMessage(message);
    }
    
    private void showPurchasedBooks(Long chatId) {
        User user = userRepository.findByChatId(chatId).orElse(null);
        if (user == null) return;
        
        List<Order> orders = orderRepository.findByUser(user);
        if (orders.isEmpty()) {
            executeMessage(new SendMessage(chatId.toString(), "Siz hali hech narsa sotib olmagansiz."));
            return;
        }
        
        StringBuilder sb = new StringBuilder("📦 Sizning xaridlaringiz:\n\n");
        for (Order order : orders) {
            if ("CONFIRMED".equals(order.getStatus())) {
                sb.append(String.format("Buyurtma #%d (%s):\n", order.getId(), order.getOrderDate().toLocalDate()));
                for (OrderItem item : order.getOrderItems()) {
                    sb.append(String.format("- %s x %d\n", item.getBook().getName(), item.getQuantity()));
                }
                sb.append(String.format("Jami: %.2f so'm\n\n", order.getTotalAmount()));
            }
        }
        
        if (sb.toString().equals("📦 Sizning xaridlaringiz:\n\n")) {
             executeMessage(new SendMessage(chatId.toString(), "Sizda tasdiqlangan buyurtmalar yo'q."));
        } else {
             executeMessage(new SendMessage(chatId.toString(), sb.toString()));
        }
    }


    private void sendCollaboration(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Biz bilan bog'lanish: +99899-348-11-10");
        executeMessage(msg);
    }

    private void sendInfo(Long chatId) {
        SendMessage msg = new SendMessage(chatId.toString(), "Bot nomi: Amal\nMaqsadi: Kitob savdosi");
        executeMessage(msg);
    }
}
