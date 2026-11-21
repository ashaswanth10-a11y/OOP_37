package in.bits.campusops.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);



    @Query("select t from Technician t where t.active = true order by t.id")
    List<Technician> findActiveTechnicians();

    @Query("select a from Administrator a where a.active = true order by a.id")
    List<Administrator> findActiveAdministrators();

    @Query("select u from User u order by u.id")
    List<User> findAllOrdered();
}
