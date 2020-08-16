package com.xyz.webserver;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyz.flows.LoanApplicationCreationFlow;
import com.xyz.observer.LoanRequestObserver;
import com.xyz.states.LoanApplicationState;
import net.corda.client.jackson.JacksonSupport;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/")
public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(RestController.class);
    private final CordaRPCOps proxy;
    private final CordaX500Name me;

    public Controller(NodeRPCConnection rpc) {
        this.proxy = rpc.proxy;
        this.me = proxy.nodeInfo().getLegalIdentities().get(0).getName();

        if (me.toString().contains("XYZLoaning")) {
            Thread loanObserverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Requesting LoanRequestObserver");
                    new LoanRequestObserver(proxy, me).observe();
                }
            });
            loanObserverThread.start();
        }
    }


    public String toDisplayString(X500Name name) {
        return BCStyle.INSTANCE.toString(name);
    }

    private boolean isNotary(NodeInfo nodeInfo) {
        return !proxy.notaryIdentities()
                .stream().filter(el -> nodeInfo.isLegalIdentity(el))
                .collect(Collectors.toList()).isEmpty();
    }

    private boolean isMe(NodeInfo nodeInfo) {
        return nodeInfo.getLegalIdentities().get(0).getName().equals(me);
    }

    private boolean isNetworkMap(NodeInfo nodeInfo) {
        return nodeInfo.getLegalIdentities().get(0).getName().getOrganisation().equals("Network Map Service");
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

    @PostMapping(value = "applyForLoan")
    private ResponseEntity<String> applyForLoan(@RequestParam("companyname") String companyName,
                                                @RequestParam("businesstype") String businessType, @RequestParam("loanamount") int loanAmount) {
        try {
            logger.info("Apply for Loan called in Node : " + me.toString());

            if (!me.toString().contains("XYZLoaning")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("WRONG NODE TO PROCESS REQUEST TO");
            }
            SignedTransaction tx = proxy.startTrackedFlowDynamic(LoanApplicationCreationFlow.class, companyName, businessType, loanAmount,null,null,null).getReturnValue().get();
            ContractState applicationState = tx.getTx().getOutputs().get(0).getData();
            String loanRequestID = ((LoanApplicationState) applicationState).getLoanApplicationId().toString();
            String loanApplicationState = ((LoanApplicationState) applicationState).getApplicationStatus().toString();

            logger.info("Loan Application created : " + "Loan Application Id: " + loanRequestID + " Status : " + loanApplicationState);
            return ResponseEntity.status(HttpStatus.CREATED).body("Loan Application Id: " + loanRequestID + " Status : " + loanApplicationState);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @GetMapping(value = "states")
    private List<StateAndRef<ContractState>> requestAllStates() {
        return proxy.vaultQuery(ContractState.class).getStates();
    }

}