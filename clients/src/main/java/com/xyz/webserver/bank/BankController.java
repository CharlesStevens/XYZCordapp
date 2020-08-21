package com.xyz.webserver.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.observer.bank.BankLoanProcessingStateObserver;
import com.xyz.states.BankFinanceState;
import com.xyz.states.schema.LoaningProcessSchemas;
import com.xyz.webserver.data.ControllerStatusResponse;
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
        Map<String, String> financeStates = new HashMap<>();
        List<StateAndRef<BankFinanceState>> processingStates = proxy.vaultQuery(BankFinanceState.class).getStates();
        for (StateAndRef<BankFinanceState> stateRef : processingStates) {
            BankFinanceState financeState = stateRef.getState().getData();
            financeStates.put(financeState.getBankLoanProcessingId().getId().toString(), financeState.getBankProcessingStatus().toString());
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ControllerStatusResponse(financeStates));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @GetMapping(value = "bankProcessingStatus", produces = "application/json")
    private ResponseEntity<Object> getStatusOfApplication(@RequestParam("bankProcessingId") String bankProcessingId) {

        QueryCriteria bankProcessingIdCustomQuery;

        try {
            bankProcessingIdCustomQuery = new QueryCriteria.VaultCustomQueryCriteria(
                    Builder.equal(QueryCriteriaUtils.getField("bankProcessingId",
                            LoaningProcessSchemas.PersistentBankProcessingSchema.class), UUID.fromString(bankProcessingId)));
            List<StateAndRef<BankFinanceState>> applicationStates = proxy
                    .vaultQueryByCriteria(bankProcessingIdCustomQuery, BankFinanceState.class).getStates();

            if (applicationStates == null || applicationStates.size() == 0)
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(new HashMap<String, String>() {{
                            put(bankProcessingId, "Input BankProcessingId doesnt exists in System.");
                        }}));
            else {
                String loanApplicationStatus = applicationStates.get(0).getState().getData().getBankProcessingStatus().toString();
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ControllerStatusResponse(new HashMap<String, String>() {{
                            put(bankProcessingId, loanApplicationStatus);
                        }}));
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new LoanApplicationException(e.getMessage()));
        }

    }
}