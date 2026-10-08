#!/usr/bin/env bash
#
# GRNET fork: tests the matcher's payment entries. Builds the matcher's sources, with stub
# Credential Manager host functions (stubs.cpp) that print what the matcher reports, into a
# WASI program, and runs it under Node on requests and credential databases from fixtures.py.
#
# Needs the WASI SDK 20 the Makefile uses, Node 20 or later, and python3.
#
#   ./test/test.sh
#
set -euo pipefail

cd "$(dirname "$0")"
WASI_SDK="${WASI_SDK:-$HOME/wasi-sdk-20.0}"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT"

CXX=("$WASI_SDK/bin/clang++" -c -std=c++20 -O1 -fno-exceptions)
"$WASI_SDK/bin/clang" -c -O1 -o "$OUT/cJSON.o" ../cJSON.c
# Without __wasm__, so the stubs are plain definitions that satisfy the matcher's imports.
"${CXX[@]}" -U__wasm__ -I.. -o "$OUT/stubs.o" stubs.cpp
for f in Request CredentialDatabase cppbor cppbor_parse matcher logger dcql paths; do
    "${CXX[@]}" -o "$OUT/$f.o" "../$f.cpp"
done
"$WASI_SDK/bin/clang++" -o "$OUT/harness.wasm" "$OUT"/*.o -lstdc++
python3 fixtures.py "$OUT"

fail=0
run() { node --no-warnings run.mjs "$OUT/harness.wasm" "$PWD/$OUT" "$@"; }
expect() {
    local name="$1" pattern="$2" out="$3"
    if grep -qF -- "$pattern" <<< "$out"; then
        echo "  ok   $name"
    else
        echo "  FAIL $name: no line with '$pattern' in"
        sed 's/^/       /' <<< "$out"
        fail=1
    fi
}
expect_not() {
    local name="$1" pattern="$2" out="$3"
    if grep -qF -- "$pattern" <<< "$out"; then
        echo "  FAIL $name: unexpected '$pattern' in"
        sed 's/^/       /' <<< "$out"
        fail=1
    else
        echo "  ok   $name"
    fi
}

out=$(run signed_payment.json db.cbor)
expect "signed request: the card is the payment" \
    'PAYMENT id=0 openid4vp-v1-signed doc-dpc-1 merchant="Evergreen Coffee House" amount="38.00 EUR" method="Card 1"' "$out"
expect "signed request: the PID is shown as before" 'entry   id=0 openid4vp-v1-signed doc-pid title="PID"' "$out"

out=$(run unsigned_payment.json db.cbor)
expect "unsigned request: amount padded to minor units" 'amount="42.50 EUR"' "$out"

out=$(run unsigned_payment.json db_two_cards.cbor)
expect "two cards: the first is a payment option" 'doc-dpc-1 merchant="Evergreen Coffee House"' "$out"
expect "two cards: the second is a payment option" 'doc-dpc-2 merchant="Evergreen Coffee House"' "$out"

out=$(run no_payment.json db.cbor)
expect "no payment: the card is shown as a credential" 'entry   id=0 openid4vp-v1-unsigned doc-dpc-1 title="Card 1"' "$out"
expect_not "no payment: no payment entry" 'PAYMENT' "$out"

out=$(run string_amount.json db.cbor)
expect_not "amount as a string: no payment entry" 'PAYMENT' "$out"

out=$(run two_payments.json db.cbor)
expect_not "two payments: no payment entry" 'PAYMENT' "$out"

out=$(run payment_for_pid.json db.cbor)
expect "payment bound to another query: that credential is the payment" 'PAYMENT id=0 openid4vp-v1-unsigned doc-pid' "$out"
expect "payment bound to another query: the card is shown as a credential" 'entry   id=0 openid4vp-v1-unsigned doc-dpc-1' "$out"

out=$(run signed_payment.json db.cbor 1)
expect_not "Credential Manager runtime 1: no payment entry" 'PAYMENT' "$out"

if [[ "$fail" -eq 0 ]]; then
    echo "all passed"
else
    exit 1
fi
