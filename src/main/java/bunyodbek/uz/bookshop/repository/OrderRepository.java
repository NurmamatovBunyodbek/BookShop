package bunyodbek.uz.bookshop.repository;

import bunyodbek.uz.bookshop.model.Order;
import bunyodbek.uz.bookshop.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser(User user);
    List<Order> findByStatusAndOrderDateBetween(String status, LocalDateTime start, LocalDateTime end);
}
