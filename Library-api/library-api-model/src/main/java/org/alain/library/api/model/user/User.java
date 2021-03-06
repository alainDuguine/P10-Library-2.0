package org.alain.library.api.model.user;

import lombok.*;
import org.alain.library.api.model.loan.Loan;
import org.alain.library.api.model.reservation.Reservation;
import org.alain.library.api.model.user.validation.PasswordMatches;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "library_user")
@PasswordMatches
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Email
    @Column(nullable = false)
    private String email;

    @NotNull
    @Column(nullable = false)
    private String password;

    @Transient
    private String passwordConfirmation;

    @NotNull
    @Size( min = 2, max=30)
    @Column (length = 30)
    private String firstName;

    @NotNull
    @Size( min = 2, max=30)
    @Column (length = 30)
    private String lastName;

    private int active=1;

    @NotNull
    private boolean notification=true;

    private String roles = "";

    @Builder.Default
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("currentStatusDate desc")
    private List<Loan> loans = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("currentStatusDate desc")
    private List<Reservation> reservations = new ArrayList<>();

    public User(@NotNull @Email String email, @NotNull String password, @NotNull String passwordConfirmation, @NotNull @Size(min = 2, max = 30) String firstName, @NotNull @Size(min = 2, max = 30) String lastName) {
        this.email = email;
        this.password = password;
        this.passwordConfirmation = passwordConfirmation;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void addLoan(Loan loan){
        this.loans.add(loan);
        loan.setUser(this);
    }

    public void removeLoan(Loan loan){
        this.loans.remove(loan);
        loan.setUser(null);
    }

    public void addReservation(Reservation reservation){
        this.reservations.add(reservation);
        reservation.setUser(this);
    }

    public void removeReservation(Reservation reservation){
        this.reservations.remove(reservation);
        reservation.setUser(null);
    }

    public List<String> getRoleList(){
        if (this.roles.length() > 0){
            return Arrays.asList(this.roles.split(","));
        }
        return new ArrayList<>();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", active=" + active +
                ", roles='" + roles + '\'' +
                '}';
    }
}
