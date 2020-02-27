package org.alain.library.api.business.impl;

import lombok.extern.slf4j.Slf4j;
import org.alain.library.api.business.contract.BookManagement;
import org.alain.library.api.business.contract.ReservationManagement;
import org.alain.library.api.business.contract.UserManagement;
import org.alain.library.api.business.exceptions.ReservationException;
import org.alain.library.api.business.exceptions.UnauthorizedException;
import org.alain.library.api.business.exceptions.UnknowStatusException;
import org.alain.library.api.consumer.repository.ReservationRepository;
import org.alain.library.api.model.book.Book;
import org.alain.library.api.model.exceptions.BookAlreadyLoanedException;
import org.alain.library.api.model.exceptions.BookStillAvailableException;
import org.alain.library.api.model.exceptions.FullReservationListException;
import org.alain.library.api.model.exceptions.ReservationAlreadyExistsException;
import org.alain.library.api.model.loan.StatusDesignation;
import org.alain.library.api.model.reservation.Reservation;
import org.alain.library.api.model.reservation.ReservationStatus;
import org.alain.library.api.model.reservation.StatusEnum;
import org.alain.library.api.model.user.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ReservationManagementImpl extends CrudManagementImpl<Reservation> implements ReservationManagement {

    private final ReservationRepository reservationRepository;
    private final BookManagement bookManagement;
    private final UserManagement userManagement;


    public ReservationManagementImpl(ReservationRepository reservationRepository, BookManagement bookManagement, UserManagement userManagement) {
        super(reservationRepository);
        this.reservationRepository = reservationRepository;
        this.bookManagement = bookManagement;
        this.userManagement = userManagement;
    }

    @Override
    public List<Reservation> getReservationsByStatusAndUserIdAndBookId(String status, Long userId, Long bookId) {
        log.info("getReservation : (status:" + status + ", userId:" + userId + ", bookId: " + bookId);
        if(status != null)
            status = status.toUpperCase();
        return reservationRepository.findByCurrentStatusAndUserIdAndBookId(status, bookId, userId);
    }

    @Override
    public Reservation createNewReservation(Long bookId, Long userId, UserPrincipal userPrincipal) {
        log.info("createNewReservation : (bookId:" + bookId + ", userId:" + userId + ", UserPrincipal: " + userPrincipal.getUsername());
        Optional<Book> book = bookManagement.findOne(bookId);
        Optional<User> user = userManagement.findOne(userId);
        Reservation reservation = new Reservation();
        if(book.isPresent() && user.isPresent() && (userPrincipal.getId().equals(userId) || userPrincipal.hasRole("ADMIN"))){
            try{
                ReservationStatus status = new ReservationStatus();
                status.setDate(LocalDateTime.now());
                status.setStatus(StatusEnum.PENDING);
                reservation.addStatus(status);

                checkBookReservation(book.get());
                book.get().addReservation(reservation);

                checkUserReservation(user.get(), bookId);
                user.get().addReservation(reservation);

                reservationRepository.save(reservation);
            }catch (Exception e){
                log.warn("Wrong parameter while creating new reservation : " + e.getMessage());
                throw new ReservationException(e.getMessage());
            }
        }else{
            log.error("Unexpected request for creating reservation : " + userPrincipal.getUsername());
            throw new UnauthorizedException("Impossible to create reservation");
        }
        log.info("New reservation created : " + reservation.toString());
        return reservation;
    }

    @Override
    public Optional<Reservation> updateReservation(Long reservationId, String status, UserPrincipal userPrincipal) {
        log.info("updateReservation : (reservationId:" + reservationId + ", status:" + status + ", UserPrincipal: " + userPrincipal.getUsername());
        Optional<Reservation> reservation = reservationRepository.findById(reservationId);
        if (reservation.isPresent() && (userPrincipal.hasRole("ADMIN") || userPrincipal.getId().equals(reservation.get().getUser().getId()))){
            StatusEnum statusEnum;
            try{
               statusEnum = StatusEnum.valueOf(status.toUpperCase());
            }catch (Exception ex){
                throw new UnknowStatusException("Status " + status + " doesn't exists");
            }
            ReservationStatus reservationStatus = ReservationStatus.builder().status(statusEnum).date(LocalDateTime.now()).build();
            reservation.get().addStatus(reservationStatus);
            return Optional.of(reservationRepository.save(reservation.get()));
        }
        log.warn("Reservation update failed");
        return Optional.empty();
    }

    @Override
    public List<Reservation> updateAndGetExpiredReservation() {
        List<Reservation> reservationList = reservationRepository.findByCurrentStatusAndUserIdAndBookId(StatusEnum.RESERVED.name(), null, null);
        List<Reservation> reservationListExpired = new ArrayList<>();
        reservationList.forEach(reservation -> {
            if(reservation.getCurrentStatusDate().plusDays(2).isBefore(LocalDateTime.now())){
                reservation.addStatus(ReservationStatus.builder().date(LocalDateTime.now()).status(StatusEnum.CANCELED).build());
                reservationRepository.save(reservation);
                reservationListExpired.add(reservation);
            }
        });
        return reservationListExpired;
    }

    private void checkBookReservation(Book book){
        if (book.getNbCopiesAvailable() != 0){
            throw new BookStillAvailableException("Impossible to add reservation, book id " + book.getId() + " has " + book.getNbCopiesAvailable() + " copies available");
        }
        if (book.getReservations().size() == book.getCopyList().size() * 2){
            throw new FullReservationListException("Reservation List full : size:"+book.getReservations().size()+", book copies:"+ book.getCopyList().size());
        }
    }

    private void checkUserReservation(User user, Long bookId){
        // check if user does not have already a copy of the book
        user.getLoans().forEach(loan -> {
            if(loan.getBookCopy().getBook().getId().equals(bookId)
                    && !loan.getCurrentStatus().equals(StatusDesignation.RETURNED.toString())){
                throw new BookAlreadyLoanedException("User has already a copy of the book " + bookId);
            }
        });
        user.getReservations().forEach(userReservations -> {
            if(userReservations.getBook().getId().equals(bookId)
                    && !(userReservations.getCurrentStatus().equals(StatusEnum.CANCELED.name()) || userReservations.getCurrentStatus().equals(StatusEnum.TERMINATED.name()))){
                throw new ReservationAlreadyExistsException("User has already a current reservation for the book " + bookId);
            }
        });
    }
}
