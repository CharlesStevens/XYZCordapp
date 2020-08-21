package com.xyz.webserver.ca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.constants.CreditScoreDesc;
import com.xyz.observer.ca.CACreditScoreCheckStateObserver;
import com.xyz.states.CreditRatingState;
import com.xyz.states.schema.LoaningProcessSchemas;
import com.xyz.webserver.data.ControllerStatusResponse;
import com.xyz.webserver.data.CreditCheckRatings;
import com.xyz.webserver.data.CreditRatingStatusResponse;
import com.xyz.webserver.data.LoanApplicationException;
import com.xyz.webserver.util.NodeRPCConnection;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.StateAndRef;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class CreditAgencyController {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    @Value("${disable.observers}")
    private boolean disableObservers;

    public CreditAgencyController(NodeRPCConnection rpc) {
        this.proxy = rpc.getproxy();
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();

        if (!disableObservers) {
            Thread creditObserverThread = new Thread(
                    () -> new CACreditScoreCheckStateObserver(proxy).observeCreditCheckApplication());
            creditObserverThread.start();
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

    @GetMapping(value = "fetchAllCreditProcessingStatuses")
    private ResponseEntity<CreditRatingStatusResponse> requestAllStates() {
        Map<String, CreditCheckRatings> financeStates = new HashMap<>();
        List<StateAndRef<CreditRatingState>> processingStates = proxy.vaultQuery(CreditRatingState.class).getStates();
        for (StateAndRef<CreditRatingState> stateRef : processingStates) {
            CreditRatingState financeState = stateRef.getState().getData();
            final String creditRatingDesc = financeState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED ? "DECISION_PENDING" : financeState.getCreditScoreDesc().toString();
            final String creditScores = financeState.getCreditScoreDesc() == CreditScoreDesc.UNSPECIFIED ? "DECISION_PENDING" : financeState.getCreditScoreCheckRating().toString();
            financeStates.put(financeState.getLoanVerificationId().getId().toString(), new CreditCheckRatings(creditScores, creditRatingDesc));
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new CreditRatingStatusResponse(financeStates));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @GetMapping(value = "creditScoreStatus", produces = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestParam("creditVerficationId") String creditVerficationId) {

        QueryCriteria creditVerficationIdCustomQuery;
        try {
            creditVerficationIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("loanVerificationId",
                            LoaningProcessSchemas.PersistentCreditRatingSchema.class), UUID.fromString(creditVerficationId)));
            List<StateAndRef<CreditRatingState>> applicationStates = proxy
                    .vaultQueryByCriteria(creditVerficationIdCustomQuery, CreditRatingState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0)
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(new HashMap<String, String>() {{
                            put(creditVerficationId, "Input CreditVerificationId doesnt exists in System.");
                        }}));
            else {
                CreditScoreDesc scoreDesc = applicationStates.get(0).getState().getData().getCreditScoreDesc();
                String verificationDescription = scoreDesc == CreditScoreDesc.UNSPECIFIED ? "IN_PROCESSING/PENDING VERIFICATION CHECK" :
                        applicationStates.get(0).getState().getData().getCreditScoreDesc().toString();
                String creditScore = scoreDesc == CreditScoreDesc.UNSPECIFIED ? "NULL/DECISION_PENDING" : applicationStates.get(0).getState().getData().getCreditScoreCheckRating().toString();
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new CreditRatingStatusResponse(
                                new HashMap<String, CreditCheckRatings>() {{
                                    put(creditVerficationId, new CreditCheckRatings(creditScore, verificationDescription));
                                }}));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LoanApplicationException(e.getMessage()));
        }

    }
}