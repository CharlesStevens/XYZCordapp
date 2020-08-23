package com.xyz.webserver.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.observer.bank.BankLoanProcessingStateObserver;
import com.xyz.processor.bank.BankProcessingProcessor;
import com.xyz.states.BankFinanceState;
import com.xyz.states.schema.LoaningProcessSchemas;
import com.xyz.webserver.data.ControllerRequest;
import com.xyz.webserver.data.ControllerStatusResponse;
import com.xyz.webserver.data.LoanApplicationException;
import com.xyz.webserver.util.NodeRPCConnection;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteriaUtils;
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

import java.util.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class BankController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    @Value("${disable.observers}")
    private boolean disableObservers;

    public BankController(NodeRPCConnection rpc) {
        this.proxy = rpc.getproxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();
        if (!disableObservers) {
            Thread bankProcessingRequestThread = new Thread(
                    () -> new BankLoanProcessingStateObserver(proxy).observeBankProcessingRequest());
            bankProcessingRequestThread.start();
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

    @GetMapping(value = "fetchAllBankProcessingStates")
    private ResponseEntity<ControllerStatusResponse> requestAllStates() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<BankFinanceState>> processingStates = proxy.vaultQuery(BankFinanceState.class).getStates();
        for (StateAndRef<BankFinanceState> stateRef : processingStates) {
            BankFinanceState financeState = stateRef.getState().getData();
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.BANK_PROCESSING_ID, financeState.getBankLoanProcessingId().getId().toString());
                put(ControllerStatusResponse.STATUS, financeState.getBankProcessingStatus().toString());
            }});
        }
        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No applications founds in the system for processing");
            }});
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @PostMapping(value = "bankProcessingStatus", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestBody ControllerRequest request) {

        QueryCriteria bankProcessingIdCustomQuery;
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        try {
            bankProcessingIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("bankProcessingId",
                            LoaningProcessSchemas.PersistentBankProcessingSchema.class), UUID.fromString(request.getApplicationID())));
            List<StateAndRef<BankFinanceState>> applicationStates = proxy
                    .vaultQueryByCriteria(bankProcessingIdCustomQuery, BankFinanceState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0) {
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.BANK_PROCESSING_ID, request.getApplicationID());
                    put(ControllerStatusResponse.STATUS, "Input BankProcessingId doesnt exists in System.");
                }});
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(applicationStatus));
            } else {
                String loanApplicationStatus = applicationStates.get(0).getState().getData().getBankProcessingStatus().toString();
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.BANK_PROCESSING_ID, request.getApplicationID());
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

    @PostMapping(value = "initateBankProcess", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> initateBankProcess(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Initiating Bank processing  for BankApplicationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            BankProcessingProcessor process = new BankProcessingProcessor(
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy);
            String response = process.processLoanDisbursement();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.BANK_PROCESSING_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE : Bank processed the loan with response : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }
}