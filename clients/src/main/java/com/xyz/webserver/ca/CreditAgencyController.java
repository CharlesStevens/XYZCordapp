package com.xyz.webserver.ca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.observer.ca.CACreditScoreCheckStateObserver;
import com.xyz.processor.ca.CACreditScoreCheckProcessor;
import com.xyz.states.CreditRatingState;
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

import javax.annotation.PostConstruct;
import java.util.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class CreditAgencyController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    @Value("${disable.observers}")
    private boolean disableObservers;

    @PostConstruct
    public void init() {
        logger.info("Disable Observers property value : " + disableObservers);
        if (!disableObservers) {
            Thread creditObserverThread = new Thread(
                    () -> new CACreditScoreCheckStateObserver(proxy).observeCreditCheckApplication());
            creditObserverThread.start();
        }
    }

    public CreditAgencyController(NodeRPCConnection rpc) {
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

    @GetMapping(value = "fetchAllCreditProcessingStatuses")
    private ResponseEntity<ControllerStatusResponse> requestAllStates() {
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        List<StateAndRef<CreditRatingState>> processingStates = proxy.vaultQuery(CreditRatingState.class).getStates();
        for (StateAndRef<CreditRatingState> stateRef : processingStates) {
            CreditRatingState financeState = stateRef.getState().getData();
            final String creditRatingDesc = financeState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED ? "DECISION_PENDING" : financeState.getCreditScoreDesc().toString();
            final String creditScores = financeState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED ? "DECISION_PENDING" : financeState.getCreditScoreCheckRating().toString();
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.CREDIT_CHECK_VERIFICATION_ID, financeState.getLoanVerificationId().getId().toString());
                put(ControllerStatusResponse.CREDIT_SCORE, creditScores);
                put(ControllerStatusResponse.CREDIT_SCORE_DESC, creditRatingDesc);
            }});
        }

        if (applicationStatus.isEmpty()) {
            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.STATUS, "No Credit Check applications found in the system.");
            }});
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(applicationStatus));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @PostMapping(value = "creditScoreProcessingStatus", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestBody ControllerRequest request) {
        QueryCriteria creditVerficationIdCustomQuery;
        List<Map<String, String>> applicationStatus = new ArrayList<>();
        try {
            creditVerficationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanVerificationId",
                            LoaningProcessSchemas.PersistentCreditRatingSchema.class), UUID.fromString(request.getApplicationID())));
            List<StateAndRef<CreditRatingState>> applicationStates = proxy
                    .vaultQueryByCriteria(creditVerficationIdCustomQuery, CreditRatingState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0) {
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.CREDIT_CHECK_VERIFICATION_ID, request.getApplicationID());
                    put(ControllerStatusResponse.STATUS, "Input CreditVerificationId doesnt exists in System.");
                }});
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(applicationStatus));
            } else {
                CreditScoreDesc scoreDesc = applicationStates.get(0).getState().getData().getCreditScoreDesc();
                String verificationDescription = scoreDesc == CreditScoreDesc.UNSPECIFIED ? "IN_PROCESSING/PENDING VERIFICATION CHECK" :
                        applicationStates.get(0).getState().getData().getCreditScoreDesc().toString();
                String creditScore = scoreDesc == CreditScoreDesc.UNSPECIFIED ? "NULL/DECISION_PENDING" : applicationStates.get(0).getState().getData().getCreditScoreCheckRating().toString();
                applicationStatus.add(new HashMap<String, String>() {{
                    put(ControllerStatusResponse.CREDIT_CHECK_VERIFICATION_ID, request.getApplicationID());
                    put(ControllerStatusResponse.CREDIT_SCORE, creditScore);
                    put(ControllerStatusResponse.CREDIT_SCORE_DESC, verificationDescription);
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

    @PostMapping(value = "initiateCreditCheckProcessing", produces = "application/json", consumes = "application/json")
    private ResponseEntity<Object> initiateCreditCheckProcessing(@RequestBody ControllerRequest controllerRequest) {
        try {
            logger.info("HTTP REQUEST :Initiating CreditCheck processing  for CreditVerificationId : " + controllerRequest.getApplicationID());
            List<Map<String, String>> applicationStatus = new ArrayList<>();

            CACreditScoreCheckProcessor process = new CACreditScoreCheckProcessor(
                    new UniqueIdentifier(null, UUID.fromString(controllerRequest.getApplicationID())), proxy);
            String response = process.processCreditScoreCheck();

            applicationStatus.add(new HashMap<String, String>() {{
                put(ControllerStatusResponse.CREDIT_CHECK_VERIFICATION_ID, controllerRequest.getApplicationID());
                put(ControllerStatusResponse.STATUS, response);
            }});
            logger.info("HTTP RESPONSE :Credit Check processed with response : " + response);
            return ResponseEntity.status(HttpStatus.OK).body(new ControllerStatusResponse(applicationStatus));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new LoanApplicationException(e.getMessage()));
        }
    }
}