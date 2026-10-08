#pragma once

#include <string>
#include <vector>
#include <memory>
#include <optional>

extern "C" {
#include "cJSON.h"
}

#include "dcql.h"

// Structs for legacy/simple request parsing if needed, 
// though MdocRequest now delegates to DcqlQuery.
struct MdocRequestDataElement {
    std::string namespaceName;
    std::string dataElementName;
    bool intentToRetain;
};

struct VcRequestedClaim {
    std::string claimName;
};

// GRNET fork: a TS12 card payment, transaction_data of type urn:eudi:sca:payment:1.
struct PaymentTransaction {
    // The DCQL credential query ids it binds to.
    std::vector<std::string> credentialIds;
    std::string merchantName;
    // As shown to the user, e.g. "38.00 EUR".
    std::string amount;
};

struct Request {
    std::string protocol;
    // GRNET fork: a payment the request asks the user to approve, if any.
    std::optional<PaymentTransaction> payment;
    Request(std::string protocol_) : protocol(protocol_) {}
    virtual ~Request() = default;
};

struct MdocRequest : public Request {
    MdocRequest(
            std::string protocol_,
            DcqlQuery dcqlQuery_
    ) : Request(protocol_), dcqlQuery(dcqlQuery_) {}

    // The logic is now encapsulated in this query object
    DcqlQuery dcqlQuery;

    std::vector<Combination> getCredentialCombinations(const CredentialDatabase* db, const std::string& protocol);

    static std::unique_ptr<MdocRequest> parseMdocApi(const std::string& protocolName, cJSON *requestJson);
};

struct OpenID4VPRequest : public Request {
    OpenID4VPRequest(
            std::string protocol_,
            DcqlQuery dcqlQuery_
    ): Request(protocol_), dclqQuery(dcqlQuery_) {}

    DcqlQuery dclqQuery;

    static std::unique_ptr<OpenID4VPRequest> parseOpenID4VP(cJSON *requestJson, std::string protocolValue);
};