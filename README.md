# XYZCordapp
 Sample Cordapp application demonstrating business with Loaning application
  
Deploy Nodes
```
gradlew deployNodes
```

Run Nodes
```
build/nodes/runnodes
```

OR

Deploy And Run Nodes with below Command :

```
startapp.bat
```

Once nodes starts, start the springboot
```
gradlew bootRunFA
gradlew bootRunBank
gradlew bootRunCA
```

Find the node service 
```
{hostname}:8080/whoami              -- XYZ Company Node5
{hostname}:8090/whoami              -- Bank Node
{hostname}:8093/whoami              -- CreditCheck Node
```

Initiate Loan Application on FA Node
```
{FAhostname}:{FAPortNumber}/applyForLoan         POST
Request Body:
{
  "loanAmount": {amount of Loan},
  "borrowerCompany": {Name of the borrowing company},
  "borrowerBusinessType": {Business type of borrowing company}
}

```

Initiate Credit Check on FA Node
```
{FAhostname}:{FAPortNumber}/initiateCreditCheck         POST
Request Body:
{
  "applicationID": {Loan Application Id}
}
```

Initiate Credit Check Processing on FA Node
```
{CAhostname}:{CAPortNumber}/initiateCreditCheckProcessing         POST
Request Body:
{
  "applicationID": {LoanVerificationId from previous post request response}
}
```

Initiate Credit Check on FA Node
```
{FAhostname}:{FAPortNumber}/processCreditCheckResponse         POST
Request Body:
{
  "applicationID": {Loan Application Id}
}
```

Initiate Bank Processing on FA Node
```
{FAhostname}:{FAPortNumber}/initiateBankProcessing         POST
Request Body:
{
  "applicationID": {Loan Application Id}
}
```

Initiate Bank Loan Disbursement Process on Bank Node
```
{Bankhostname}:{BankPortNumber}/initateBankProcess         POST
Request Body:
{
  "applicationID": {BankFinanceId from previous post response}
}
```

Initiate Bank Processing Response on FA Node
```
{FAhostname}:{FAPortNumber}/processBankProcessingResponse         POST
Request Body:
{
  "applicationID": {Loan Application Id}
}
```

Check the status of the application on FA node at any timeline of a Loan processing
```
{FAhostname}:{FAPortNumber}/statusOfApplication         POST
Request Body:
{
  "applicationID": {Loan Application Id}
}
```

Check all the Loan Application statuses in the System of FA.
```
{FAhostname}:{FAPortNumber}/getAllBankLoanApplicationStatuses         GET
```

Check all the Pending Loan Application statuses in the System of FA.
```
{FAhostname}:{FAPortNumber}/getAllBankLoanPendingStatuses         GET
```

Check all the Processed Loan Application statuses in the System of FA.
```
{FAhostname}:{FAPortNumber}/getAllBankLoanProcessedStatuses         GET
```

Check all the Declined/Rejected Loan Application statuses in the System of FA.
```
{FAhostname}:{FAPortNumber}/getAllBankLoanDeclinedStatuses         GET
```

Check all the CreditVerfication Application statuses in the System of CA.
```
{CAhostname}:{CAPortNumber}/fetchAllCreditProcessingStatuses         GET
```

Check the Credit Verfication status of a CreditVerificationId in the System of CA.
```
{CAhostname}:{CAPortNumber}/creditScoreProcessingStatus         POST
Request Body:
{
  "applicationID": {CreditVerificationId}
}
```

Check all the BankProcessing Application statuses in the System of Bank.
```
{Bankhostname}:{BankPortNumber}/fetchAllBankProcessingStates         GET
```

Check the Credit Verfication status of a CreditVerificationId in the System of Bank.
```
{Bankhostname}:{BankPortNumber}/bankProcessingStatus         POST
Request Body:
{
  "applicationID": {CreditVerificationId}
}
```



