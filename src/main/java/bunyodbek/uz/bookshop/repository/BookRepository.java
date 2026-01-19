package bunyodbek.uz.bookshop.repository;

import bunyodbek.uz.bookshop.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByDeletedFalse();
    List<Book> findByCategoryAndDeletedFalse(String category);
}
