package org.alain.library.api.service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.alain.library.api.business.contract.LoanManagement;
import org.alain.library.api.business.contract.ReservationManagement;
import org.alain.library.api.business.exceptions.UnknowStatusException;
import org.alain.library.api.business.exceptions.UnknownLoanException;
import org.alain.library.api.business.impl.UserPrincipal;
import org.alain.library.api.model.loan.Loan;
import org.alain.library.api.model.loan.LoanStatus;
import org.alain.library.api.service.dto.LoanDto;
import org.alain.library.api.service.dto.LoanForm;
import org.alain.library.api.service.dto.LoanStatusDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

import static org.alain.library.api.service.api.Converters.*;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-10-31T15:23:24.407+01:00")

@Controller
@Slf4j
public class LoansApiController implements LoansApi {

    private final ObjectMapper objectMapper;
    private final LoanManagement loanManagement;
    private final ReservationManagement reservationManagement;
    private final HttpServletRequest request;

    @org.springframework.beans.factory.annotation.Autowired
    public LoansApiController(ObjectMapper objectMapper, LoanManagement loanManagement, ReservationManagement reservationManagement, HttpServletRequest request) {
        this.objectMapper = objectMapper;
        this.loanManagement = loanManagement;
        this.reservationManagement = reservationManagement;
        this.request = request;
    }

    public ResponseEntity<LoanDto> getLoan(@ApiParam(value = "Id of loan to return",required=true) @PathVariable("id") Long id) {
        log.info("Getting loan " + id);
        Optional<Loan> loan = loanManagement.findOne(id);
        if(loan.isPresent()){
            return new ResponseEntity<LoanDto>(convertLoanModelToLoanDto(loan.get()), HttpStatus.OK);
        }
        log.warn("Unknown loan " + id);
        return new ResponseEntity<LoanDto>(HttpStatus.NOT_FOUND);
    }

    public ResponseEntity<List<LoanDto>> getLoans(@ApiParam(value = "Status values as filter in research", allowableValues = "loaned, returned, prolonged")
                                                  @Valid @RequestParam(value = "status", required = false) String status,
                                                  @ApiParam(value = "User id as filter in research") @Valid @RequestParam(value = "user", required = false) Long user) {
        List<Loan> loanList = loanManagement.findLoansByStatusAndUserId(status, user);
        log.info("Getting list loans : " + loanList.size() + " status : " + status + " user : " + user);
        return new ResponseEntity<List<LoanDto>>(convertListLoanModelToListLoanDto(loanList), HttpStatus.OK);
    }

    public ResponseEntity<List<LoanStatusDto>> getLoanHistory(@ApiParam(value = "Id of loan",required=true) @PathVariable("id") Long id) {
        try {
            log.info("Getting loan history : " + id);
            List<LoanStatus> loanStatusListModel = loanManagement.getLoanStatusList(id);
            return new ResponseEntity<List<LoanStatusDto>>(convertListLoanStatusModelToListLoanStatusDto(loanStatusListModel), HttpStatus.OK);
        }catch (UnknownLoanException ex){
            log.warn("Unknown loan " + id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public ResponseEntity<List<LoanDto>> getLoansByBookId(@ApiParam(value = "User identification" ,required=true) @RequestHeader(value="Authorization", required=true) String authorization,@ApiParam(value = "BookId as filter in research") @Valid @RequestParam(value = "bookId", required = false) Long bookId) {
        log.info("Retrieving loans for bookId {}", bookId);
        List<Loan> loanList = loanManagement.findLoansByBookId(bookId);
        log.info("Found {} loans for bookId {}", loanList.size(), bookId);
        return new ResponseEntity<List<LoanDto>>(convertListLoanModelToListLoanDto(loanList),HttpStatus.OK);
    }

    public ResponseEntity<LoanDto> addLoan(@ApiParam(value = "Loan that needs to be added to the database" ,required=true )  @Valid @RequestBody LoanForm loanForm) {
        try {
            log.info("creating new loan : user - " + loanForm.getUserId() + "bookCopy - " + loanForm.getCopyId());
            Loan loanModel = loanManagement.createNewLoan(loanForm.getCopyId(), loanForm.getUserId());
            log.info("New loan created " + loanModel.getId());
            return new ResponseEntity<LoanDto>(convertLoanModelToLoanDto(loanModel), HttpStatus.OK);
        }catch(Exception ex){
            log.warn("Impossible to create loan : user - " + loanForm.getUserId() + "bookCopy - " + loanForm.getCopyId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public ResponseEntity<Void> updateLoan(@ApiParam(value = "Id of loan to update",required=true) @PathVariable("id") Long id,
                                           @NotNull @ApiParam(value = "Status values to add to loan history", required = true, allowableValues = "loaned, returned, prolonged, late")
                                           @Valid @RequestParam(value = "status", required = true) String status) {
        try {
            log.info("Update loan : " + id + " - " + status);
            Optional<Loan> loan = loanManagement.updateLoan(id, status);
            // TODO check
            reservationManagement.checkPendingListAndNotify(loan.get().getBookCopy().getBook().getId());
            return new ResponseEntity<Void>(HttpStatus.OK);
        }catch (UnknowStatusException ex){
            log.info("Impossible to update loan : " + id + " - " + status);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public ResponseEntity<Void> extendLoan(@ApiParam(value = "Id of loan",required=true) @PathVariable("id") Long id,
                                           @ApiParam(value = "User identification" ,required=true) @RequestHeader(value="Authorization", required=true) String authorization) {
        try {
            log.info("Extending loan : " + id);
            UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                Optional<LoanStatus> loanStatus = loanManagement.extendLoan(id, userPrincipal.getId(), userPrincipal.hasRole("ADMIN"));
                if (loanStatus.isPresent()) {
                    return new ResponseEntity<Void>(HttpStatus.OK);
                } else {
                    log.warn("Loan extension impossible : " + id);
                    return new ResponseEntity<Void>(HttpStatus.BAD_REQUEST);
                }
        }catch(Exception ex){
            log.warn("Unknown loan :" + id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public ResponseEntity<List<LoanDto>> checkAndGetLateLoans(@ApiParam(value = "User identification" ,required=true)
                                                              @RequestHeader(value="Authorization", required=true) String authorization) {
        log.info("Batch call to retrieve late Loans");
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(userPrincipal.hasRole("ADMIN")){
            List<Loan> loanList = loanManagement.updateAndFindLateLoans();
            return new ResponseEntity<List<LoanDto>>(convertListLoanModelToListLoanDto(loanList),HttpStatus.OK);
        }else{
            log.warn("Unauthorized batch call " + userPrincipal.getId());
            return new ResponseEntity<List<LoanDto>>(HttpStatus.UNAUTHORIZED);
        }
    }

    public ResponseEntity<List<LoanDto>> checkAndGetFutureLateLoans(@ApiParam(value = "User identification" ,required=true)
                                                                    @RequestHeader(value="Authorization", required=true) String authorization,
                                                                    @ApiParam(value = "the day limit to check the loans", defaultValue = "1")
                                                                    @Valid @RequestParam(value = "days", required = false, defaultValue="1") Integer days) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Batch call to retrieve future late Loans within {} day(s), user : {}", days, userPrincipal.getUsername());
        if(userPrincipal.hasRole("ADMIN")){
            List<Loan> loanList = loanManagement.findFutureLateLoans(days);
            log.info("{} loans retrieved", loanList.size());
            return new ResponseEntity<List<LoanDto>>(convertListLoanModelToListLoanDto(loanList),HttpStatus.OK);
        }else{
            log.warn("Unauthorized batch call " + userPrincipal.getId());
            return new ResponseEntity<List<LoanDto>>(HttpStatus.UNAUTHORIZED);
        }
    }
}
