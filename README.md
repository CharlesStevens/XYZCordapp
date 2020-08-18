# XYZCordapp
 Sample Cordapp application demonstrating business with Loaning applications
 
 Deploy Nodes
 ```
gradlew deployNodes
```

Run Nodes
```
build/nodes/runnodes
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

Initiate Loan Application
```
{hostname}:8080/applyForLoan?companyname=ABCD&businesstype=RealEstate&loanamount=20123

```

Get States transaction in the Vault 
```
{hostname}:8080/states              -- XYZ Company Node
{hostname}:8090/states              -- Bank Node
{hostname}:8093/states              -- CreditCheck Node
```
