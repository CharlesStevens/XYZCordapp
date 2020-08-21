package com.xyz.webserver.fa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.observer.fa.FABankFinanceStateObserver;
import com.xyz.observer.fa.FACreditScoreCheckStateObserver;
import com.xyz.observer.fa.FALoanApplicationStateObserver;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import com.xyz.webserver.data.ControllerStatusResponse;
import com.xyz.webserver.data.LoanApplicationData;
import com.xyz.webserver.data.LoanApplicationException;
import com.xyz.webserver.util.NodeRPCConnection;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
import net.corda.core.transactions.SignedTransaction;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class FinanceAgencyController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    @Value("${disable.observers}")
    private boolean disableObservers;

    public FinanceAgencyController(NodeRPCConnection rpc) {
        this.proxy = rpc.getproxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();

        if (!disableObservers) {
            Thread loanObserverThread = new Thread(
                    () -> new FALoanApplicationStateObserver(proxy).observeLoanApplicationUpdate());
            Thread creditAgencyResponseObserverThread = new Thread(
                    () -> new FACreditScoreCheckStateObserver(proxy).observeCreditAgencyResponse());
            Thread bankStateObserverThread = new Thread(
                    () -> new FABankFinanceStateObserver(proxy).observeBankFinanceState());

            loanObserverThread.start();
            creditAgencyResponseObserverThread.start();
            bankStateObserverThread.start();
        }
    }

    public String toDisplayString(X500Name name) {
        return BCStyle.INSTANCE.toString(name);
    }

    @Configuration
    class Plugin {
        @Bean
        public ObjectMapper registerModule() {
            return JacksonSupport.createNonRpcMapper();
        }
    }

    @GetMapping(value = "/whoami", produces = APPLICATION_JSON_VALUE)
    private HashMap<String, String> whoami() {
        HashMap<String, String> myMap = new HashMap<>();
        myMap.put("me", me.toString());
        return myMap;
    }

    @PostMapping(value = "applyForLoan", consumes = "application/json", produces = "application/json")
    private ResponseEntity<Object> applyForLoan(@RequestBody LoanApplicationData applicationData) {
        try {
            logger.info("HTTP REQUEST : Apply for Loan called in Node : " + me.toString());

            SignedTransaction tx = proxy
                    .startTrackedFlowDynamic(LoanApplicationCreationFlow.class, applicationData.getBorrowerCompany(),
                            applicationData.getBorrowerCompany(), applicationData.getLoanAmount())
                    .getReturnValue().get();
            ContractState applicationState = tx.getTx().getOutputs().get(0).getData();
            String loanRequestID = ((LoanApplicationState) applicationState).getLoanApplicationId().toString();
            String loanApplicationState = ((LoanApplicationState) applicationState).getApplicationStatus().toString();

            logger.info("HTTP RESPONSE : Loan Application created : " + "Loan Application Id: " + loanRequestID
                    + " Status : " + loanApplicationState);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ControllerStatusResponse(new HashMap<String, String>() {{
                        put(loanRequestID, loanApplicationState);
                    }}));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }

    @GetMapping(value = "getAllBankLoanApplicationStatuses")
    private ResponseEntity<ControllerStatusResponse> requestAllStates() {
        Map<String, String> financeStates = new HashMap<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            financeStates.put(financeState.getLoanApplicationId().getId().toString(), financeState.getApplicationStatus().toString());
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(financeStates));
    }

    @GetMapping(value = "getAllBankLoanPendingStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanPendingStatuses() {
        Map<String, String> financeStates = new HashMap<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() != LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED || financeState.getApplicationStatus() != LoanApplicationStatus.LOAN_DISBURSED) {
                financeStates.put(financeState.getLoanApplicationId().getId().toString(), financeState.getApplicationStatus().toString());
            }
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(financeStates));
    }

    @GetMapping(value = "getAllBankLoanProcessedStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanProcessedStatuses() {
        Map<String, String> financeStates = new HashMap<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() == LoanApplicationStatus.LOAN_DISBURSED) {
                financeStates.put(financeState.getLoanApplicationId().getId().toString(), financeState.getApplicationStatus().toString());
            }
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(financeStates));
    }

    @GetMapping(value = "getAllBankLoanDeclinedStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanDeclinedStatuses() {
        Map<String, String> financeStates = new HashMap<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() == LoanApplicationStatus.REJECTED_FROM_BANK || financeState.getApplicationStatus() == LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED) {
                financeStates.put(financeState.getLoanApplicationId().getId().toString(), financeState.getApplicationStatus().toString());
            }
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(financeStates));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @GetMapping(value = "statusOfApplication", produces = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestParam("loanApplicationId") String loanApplicationId) {

        QueryCriteria loanApplicationIdCustomQuery;

        try {
            loanApplicationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanApplicationId",
                            LoaningProcessSchemas.PersistentLoanApplicationState.class), UUID.fromString(loanApplicationId)));
            List<StateAndRef<LoanApplicationState>> applicationStates = proxy
                    .vaultQueryByCriteria(loanApplicationIdCustomQuery, LoanApplicationState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0)
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(new HashMap<String, String>() {{
                            put(loanApplicationId, "Input LoanApplicationID doesnt exists in System.");
                        }}));
            else {
                final String loanApplicationStatus = applicationStates.get(0).getState().getData().getApplicationStatus().toString();
                return ResponseEntity.status(HttpStatus.OK)
                        .body(
                                new ControllerStatusResponse(new HashMap<String, String>() {{
                                    put(loanApplicationId, loanApplicationStatus);
                                }}));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LoanApplicationException(e.getMessage()));
        }

    }

}