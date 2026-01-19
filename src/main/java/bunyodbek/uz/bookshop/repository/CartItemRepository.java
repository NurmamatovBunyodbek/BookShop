package bunyodbek.uz.bookshop.repository;

import bunyodbek.uz.bookshop.model.Book;
import bunyodbek.uz.bookshop.model.CartItem;
import bunyodbek.uz.bookshop.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);

    Optional<CartItem> findByUserAndBook(User user, Book book);

    @Transactional
    void deleteByBookId(Long bookId);
}
