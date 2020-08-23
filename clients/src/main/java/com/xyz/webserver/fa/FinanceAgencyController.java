package com.xyz.webserver.fa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.constants.LoanApplicationStatus;
import com.xyz.flows.fa.LoanApplicationCreationFlow;
import com.xyz.observer.fa.FABankFinanceStateObserver;
import com.xyz.observer.fa.FACreditScoreCheckStateObserver;
import com.xyz.observer.fa.FALoanApplicationStateObserver;
import com.xyz.processor.fa.FABankProcessInitiationProcessor;
import com.xyz.processor.fa.FACreditCheckInitiationProcessor;
import com.xyz.processor.fa.FAPostBankStatusUpdateProcessor;
import com.xyz.processor.fa.FAPostCreditCheckProcessor;
import com.xyz.states.LoanApplicationState;
import com.xyz.states.schema.LoaningProcessSchemas;
import com.xyz.webserver.data.ControllerRequest;
import com.xyz.webserver.data.ControllerStatusResponse;
import com.xyz.webserver.data.LoanApplicationData;
import com.xyz.webserver.data.LoanApplicationException;
import com.xyz.webserver.util.NodeRPCConnection;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
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

import javax.annotation.PostConstruct;
import java.util.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class FinanceAgencyController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    @Value("${disable.observers}")
    private boolean disableObservers;

    @PostConstruct
    public void init() {
        logger.info("Disable Observers property value : " + disableObservers);

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

    public FinanceAgencyController(NodeRPCConnection rpc) {
        this.proxy = rpc.getproxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();
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
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            SignedTransaction tx = proxy
                    .startTrackedFlowDynamic(LoanApplicationCreationFlow.class, applicationData.getBorrowerCompany(),
                            applicationData.getBorrowerCompany(), applicationData.getLoanAmount())
                    .getReturnValue().get();
            ContractState applicationState = tx.getTx().getOutputs().get(0).getData();
            String loanRequestID = ((LoanApplicationState) applicationState).getLoanApplicationId().toString();
            String loanApplicationState = ((LoanApplicationState) applicationState).getApplicationStatus().toString();

            logger.info("HTTP RESPONSE : Loan Application created : " + "Loan Application Id: " + loanRequestID
                    + " Status : " + loanApplicationState);
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, loanRequestID);
                put(ControllerStatusResponse.STATUS, loanApplicationState);
            }});
            return ResponseEntity.status(HttpStatus.CREATED).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }

    @GetMapping(value = "getAllBankLoanApplicationStatuses")
    private ResponseEntity<ControllerStatusResponse> requestAllStates() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, financeState.getLoanApplicationId().getId().toString());
                put(ControllerStatusResponse.STATUS, financeState.getApplicationStatus().toString());
            }});
        }

        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No applications found in the system");
            }});
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @GetMapping(value = "getAllBankLoanPendingStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanPendingStatuses() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() != LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED || financeState.getApplicationStatus() != LoanApplicationStatus.LOAN_DISBURSED) {
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.LOAN_APPLICATION_ID, financeState.getLoanApplicationId().getId().toString());
                    put(ControllerStatusResponse.STATUS, financeState.getApplicationStatus().toString());
                }});
            }
        }

        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No Pending/In-Processing applications found in the system");
            }});
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @GetMapping(value = "getAllBankLoanProcessedStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanProcessedStatuses() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() == LoanApplicationStatus.LOAN_DISBURSED) {
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.LOAN_APPLICATION_ID, financeState.getLoanApplicationId().getId().toString());
                    put(ControllerStatusResponse.STATUS, financeState.getApplicationStatus().toString());
                }});
            }
        }

        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No Processed/Disbursed applications found in the system");
            }});
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @GetMapping(value = "getAllBankLoanDeclinedStatuses")
    private ResponseEntity<ControllerStatusResponse> getAllBankLoanDeclinedStatuses() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<LoanApplicationState>> processingStates = proxy.vaultQuery(LoanApplicationState.class).getStates();
        for (StateAndRef<LoanApplicationState> stateRef : processingStates) {
            LoanApplicationState financeState = stateRef.getState().getData();
            if (financeState.getApplicationStatus() == LoanApplicationStatus.REJECTED_FROM_BANK || financeState.getApplicationStatus() == LoanApplicationStatus.CREDIT_SCORE_CHECK_FAILED) {
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.LOAN_APPLICATION_ID, financeState.getLoanApplicationId().getId().toString());
                    put(ControllerStatusResponse.STATUS, financeState.getApplicationStatus().toString());
                }});
            }
        }

        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No Declined/Rejected applications found in the system");
            }});
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @PostMapping(value = "statusOfApplication", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestBody ControllerRequest controllerRequest) {
        QueryCriteria loanApplicationIdCustomQuery;
        List<Map<String, String>> applicationStatus = null;
        try {
            loanApplicationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanApplicationId",
                            LoaningProcessSchemas.PersistentLoanApplicationState.class), UUID.fromString(controllerRequest.getApplicationID())));
            List<StateAndRef<LoanApplicationState>> applicationStates = proxy
                    .vaultQueryByCriteria(loanApplicationIdCustomQuery, LoanApplicationState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0) {
                applicationStatus = new ArrayList<>();
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                    put(ControllerStatusResponse.STATUS, "Input LoanApplicationID doesnt exists in System.");
                }});
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(applicationStatus));
            } else {
                final String loanApplicationStatus = applicationStates.get(0).getState().getData().getApplicationStatus().toString();
                applicationStatus = new ArrayList<>();
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                    put(ControllerStatusResponse.STATUS, loanApplicationStatus);
                }});
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(applicationStatus));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LoanApplicationException(e.getMessage()));
        }

    }


    @PostMapping(value = "initiateCreditCheck", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> initiateCreditCheckVerification(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Initiating Credit Check Verification for LoanApplicationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            FACreditCheckInitiationProcessor process = new FACreditCheckInitiationProcessor(
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy);
            String response = process.processCreditCheckInitiation();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE : Credit check initiation processed with Response : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }

    @PostMapping(value = "processCreditCheckResponse", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> processCreditCheckResponse(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Initiating Credit Check Response for LoanApplicationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            FAPostCreditCheckProcessor process = new FAPostCreditCheckProcessor(null,
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy, null);
            String response = process.processCreditScores();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE : Credit check Response processed with message : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }

    @PostMapping(value = "initiateBankProcessing", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> initiateBankProcessing(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Initiating Bank processing for LoanApplicationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            FABankProcessInitiationProcessor process = new FABankProcessInitiationProcessor(
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy);
            String response = process.processBankInitiation();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE : Bank Processing finished with message : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }

    @PostMapping(value = "processBankProcessingResponse", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> processBankProcessingResponse(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Processing Bank response update for LoanApplicationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            FAPostBankStatusUpdateProcessor process = new FAPostBankStatusUpdateProcessor(null,
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy, null);
            String response = process.processBankFinanceProcessingUpdate();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.LOAN_APPLICATION_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE : Bank Response update processed with message : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }
}