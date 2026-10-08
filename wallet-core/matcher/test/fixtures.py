"""Writes the test's credential databases and requests into the directory given.

The databases follow the CBOR layout wallet-core's DCAPICredentialRegistry registers. The
requests are OpenID4VP over the Digital Credentials API, asking for a DPC and a PID, the way the
WE BUILD payment relying parties do. No dependencies: CBOR is encoded here.
"""
import base64
import json
import sys


def _head(major, n):
    if n < 24:
        return bytes([major << 5 | n])
    for ai, size in ((24, 1), (25, 2), (26, 4), (27, 8)):
        if n < 1 << (8 * size):
            return bytes([major << 5 | ai]) + n.to_bytes(size, "big")


def cbor(o):
    if isinstance(o, bytes):
        return _head(2, len(o)) + o
    if isinstance(o, str):
        b = o.encode()
        return _head(3, len(b)) + b
    if isinstance(o, list):
        return _head(4, len(o)) + b"".join(cbor(x) for x in o)
    if isinstance(o, dict):
        return _head(5, len(o)) + b"".join(cbor(k) + cbor(v) for k, v in o.items())
    raise TypeError(type(o))


def b64url(b):
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


PID_VCT = "urn:eudi:pid:1"
DPC_VCT = "https://example.com/sca-card-dpc/1.0"
PROTOCOLS = ["openid4vp-v1-signed", "openid4vp-v1-unsigned"]


def credential(document_id, title, vct, claims, bitmap):
    return {
        "title": title,
        "subtitle": "Test Wallet",
        "bitmap": bitmap,
        "protocols": PROTOCOLS,
        "sdjwt": {
            "documentId": document_id,
            "vct": vct,
            "claims": {k: [k, v, v] for k, v in claims.items()},
        },
    }


PID = credential("doc-pid", "PID", PID_VCT,
                 {"given_name": "Maria", "family_name": "Papadopoulou"}, b"pid-icon")


def card(n):
    return credential(f"doc-dpc-{n}", f"Card {n}", DPC_VCT,
                      {"credential_id": f"c{n}", "network": "mastercard", "card_id": f"k{n}"},
                      b"card-art")


def openid4vp(transaction_data=None):
    data = {
        "response_type": "vp_token",
        "response_mode": "dc_api",
        "nonce": "n-0S6_WzA2Mj",
        "dcql_query": {"credentials": [
            {"id": "card", "format": "dc+sd-jwt", "meta": {"vct_values": [DPC_VCT]},
             "claims": [{"path": ["credential_id"]}, {"path": ["network"]}, {"path": ["card_id"]}]},
            {"id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": [PID_VCT]},
             "claims": [{"path": ["given_name"]}, {"path": ["family_name"]}]},
        ]},
    }
    if transaction_data is not None:
        data["transaction_data"] = [b64url(json.dumps(td).encode()) for td in transaction_data]
    return data


def payment(amount, payee="Evergreen Coffee House", credential_ids=("card",)):
    return {
        "type": "urn:eudi:sca:payment:1",
        "credential_ids": list(credential_ids),
        "transaction_data_hashes_alg": ["sha-256"],
        "payload": {"transaction_id": "t-1", "payee": {"name": payee, "id": "p-1"},
                    "currency": "EUR", "amount": amount},
    }


def unsigned(data):
    return {"requests": [{"protocol": "openid4vp-v1-unsigned", "data": data}]}


def signed(data):
    # The matcher reads the request object's payload and does not check the signature.
    header = b64url(json.dumps({"alg": "ES256", "typ": "oauth-authz-req+jwt"}).encode())
    jwt = f"{header}.{b64url(json.dumps(data).encode())}.c2lnbmF0dXJl"
    return {"requests": [{"protocol": "openid4vp-v1-signed", "data": {"request": jwt}}]}


def write(directory):
    files = {
        "db.cbor": cbor({"protocols": [], "credentials": [PID, card(1)]}),
        "db_two_cards.cbor": cbor({"protocols": [], "credentials": [PID, card(1), card(2)]}),
        "signed_payment.json": signed(openid4vp([payment(38)])),
        "unsigned_payment.json": unsigned(openid4vp([payment(42.5)])),
        "no_payment.json": unsigned(openid4vp()),
        "string_amount.json": unsigned(openid4vp([payment("42.50")])),
        "two_payments.json": unsigned(openid4vp([payment(1), payment(2)])),
        "payment_for_pid.json": unsigned(openid4vp([payment(5, credential_ids=["pid"])])),
    }
    for name, content in files.items():
        mode = "wb" if isinstance(content, bytes) else "w"
        with open(f"{directory}/{name}", mode) as f:
            f.write(content if isinstance(content, bytes) else json.dumps(content))


if __name__ == "__main__":
    write(sys.argv[1])
