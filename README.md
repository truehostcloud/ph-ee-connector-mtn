git pu# MTN-RW Connector

This connector incorporates job worker implementations responsible for polling available jobs in Zeebe and communicating with MTN Rwanda's endpoint.
It currently facilitates the initiation of payment (RequesttoPay) and receipt of payment confirmations through MTN-Rwanda endpoints.


This connector is a Zeebe project that is part of the OAF Payment Hub EE setup. See
the [Payment Hub EE documentation](https://mifos.gitbook.io/docs/payment-hub-ee/overview)
for more information about Zeebe projects and Payment Hub in general.

## Badges

[![Build Status](https://dev.azure.com/OAFDev/prd-pipelines/_apis/build/status/one-acre-fund.ph-ee-connector-mtn?branchName=develop)](https://dev.azure.com/OAFDev/prd-pipelines/_apis/build/status/one-acre-fund.ph-ee-connector-mtn?branchName=develop)

## Tech Stack

- Java 11
- Spring Boot
- Apache Camel
- Zeebe Java Client

## Getting Started

Clone the project

  ```bash
    git clone https://github.com/one-acre-fund/ph-ee-connector-mtn.git
    cd ph-ee-connector-mtn
  ```

This connector is expected to be run alongside other connectors/services. It depends on some of
those services being up
and healthy. For local development, the services that are most critical for running this project
have been included in
the `docker-compose.yml` file. The following components are included:

- Zeebe: A workflow engine for microservices orchestration. This must be running in a healthy state
  otherwise errors
  will occur when the services below attempt to connect to it.
- Zeebe-ops: Provides APIs for carrying out certain operations on zeebe such as uploading a bpmn
  file
- Channel-connector: Provides APIs for initiating collection requests
- Roster-connector: Acts as Account management system(CLIF) - any other ams connector can be used

A lot more services can be added to the above based on your needs, but to run the mtn-rw 
connector locally,
the ones listed above are the required minimum.
Please note that the `docker-compose.yml` file in this repository should NOT be used in a production
environment.

## Running with Docker

Some images listed in the `docker-compose.yml` are available on OAF's Azure Container Registry (
ACR). To be able to pull
them, certain permissions must be granted to your azure account. Follow the steps below to
successfully run the project:

- Ensure [Docker](https://docs.docker.com/get-docker/) is installed on your machine

- Authenticate with
  azure. [Install the Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli)
  on your machine if it's not already available, and then run the command below

  ```bash
      az acr login -n oaftech # Log in to OAF's ACR through the Docker CLI.
   ```

- Run the project:

  Update `src/main/resources/application.yml` with the appropriate values where necessary, or
  provide the
  values as environment variables in the `services.fineract-connector.environment` section of
  the `docker-compose.yml`
  file, and run the command below:

  ```bash
      docker compose up -d
   ```

## Usage

To initiate a request, follow the steps below:

  - Upload the MTN-RW bpmn (found in `src/main/resources/momo_flow_roster-rwoaf.bpmn`) through **zeebe-ops**
    by sending a POST request to `http://localhost:5001/zeebe/upload` with the file attached.

  - Send a collection request through the **channel-connector** by sending a POST request
    to `http://localhost:5002/channel/collection` with Platform-TenantId as rw-oaf in the headers,
    with a sample body as shown below:
    ```json
    {
      "payer": [
          {
              "key": "MSISDN",
              "value" :"467331234501"
          },
          {
              "key":"ACCOUNTID",
              "value":"24450520"
          }
      ],
      "amount": {
          "amount": "1",
          "currency": "EUR"
      },
      "transactionType": {
          "subScenario": "BUYGOODS",
          "initiator": "PAYEE",
          "initiatorType": "BUSINESS"
      }
    }
    ```
    - Check the logs in the **mtn-rw-connector** container to see that a payment has been initiated. For the sandbox,
      a callback will not be sent back. We can simulate it ourselves to continue the workflow by calling the endpoint
      `http://localhost:5004/buygoods/callback` with the following body
      ```json
      {
        "financialTransactionId": "1663251507",
        "externalId": "{TransactionID received from the previous channel connector endpoint}",
        "amount": "1",
        "currency": "EUR",
        "payer": {
            "partyIdType": "MSISDN",
            "partyId": "46733123451"
            },
        "status":"SUCCESSFUL"
      }
      ```

    - Check the logs in the **mtn-rw-connector** connector to see if the confirmation is processed
    - Check the logs in the **roster-connector** ams connector to see if the settlement is processed in the ams

## Troubleshooting

If an error occurs while carrying out any of the steps above, check if the zeebe container is in a
healthy state by
either viewing its state through `docker ps` or sending a GET request
to `http://localhost:9600/health`.
If the zeebe container shows state as unhealthy or the health endpoint doesn't return a 204 status
response, restart the
zeebe container.

## Contributing

See `CONTRIBUTING.md` for ways to get started.