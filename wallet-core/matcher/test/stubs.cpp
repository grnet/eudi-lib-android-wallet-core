// Native harness for the Credman matcher: stub Credential Manager host functions that print
// what the matcher reports, reading the request and the credential database from files.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

extern "C" {
#include "credentialmanager.h"
}

static std::vector<char> readFile(const char* env) {
    const char* path = getenv(env);
    FILE* f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s=%s\n", env, path ? path : "(unset)"); exit(2); }
    std::vector<char> data;
    char buf[65536];
    size_t n;
    while ((n = fread(buf, 1, sizeof buf, f)) > 0) data.insert(data.end(), buf, buf + n);
    fclose(f);
    return data;
}

static std::vector<char> request, creds;
static const char* s(const char* p) { return p ? p : "(null)"; }

extern "C" {
void GetWasmVersion(uint32_t* v) { *v = getenv("WASM_VERSION") ? atoi(getenv("WASM_VERSION")) : 2; }
void GetCallingAppInfo(CallingAppInfo* info) { memset(info, 0, sizeof *info); }
void GetRequestSize(uint32_t* size) { *size = request.size() + 1; }
void GetRequestBuffer(void* buffer) { memcpy(buffer, request.data(), request.size()); ((char*) buffer)[request.size()] = 0; }
void GetCredentialsSize(uint32_t* size) { *size = creds.size(); }
size_t ReadCredentialsBuffer(void* buffer, size_t offset, size_t len) { memcpy(buffer, creds.data() + offset, len); return len; }

void AddEntrySet(char* set_id, int set_length) { printf("set %s length=%d\n", s(set_id), set_length); }
void AddEntryToSet(char* cred_id, char* icon, size_t icon_len, char* title, char* subtitle, char* disclaimer, char* warning, char* metadata, char* set_id, int set_index) {
    printf("  [%d] entry   id=%s title=\"%s\" subtitle=\"%s\" icon=%zu bytes\n", set_index, s(cred_id), s(title), s(subtitle), icon_len);
}
void AddFieldToEntrySet(char* cred_id, char* name, char* value, char* set_id, int set_index) {
    printf("  [%d]   field \"%s\"\n", set_index, s(name));
}
void AddPaymentEntryToSetV2(char* cred_id, char* merchant_name, char* payment_method_name, char* payment_method_subtitle, char* icon, size_t icon_len, char* amount, char* bank_icon, size_t bank_icon_len, char* provider_icon, size_t provider_icon_len, char* additional_info, char* metadata, char* set_id, int set_index) {
    printf("  [%d] PAYMENT id=%s merchant=\"%s\" amount=\"%s\" method=\"%s\" subtitle=\"%s\" icon=%zu bytes\n", set_index, s(cred_id), s(merchant_name), s(amount), s(payment_method_name), s(payment_method_subtitle), icon_len);
}
void AddStringIdEntry(char* cred_id, char* icon, size_t icon_len, char* title, char* subtitle, char* disclaimer, char* warning) {
    printf("  v1 entry id=%s title=\"%s\"\n", s(cred_id), s(title));
}
void AddFieldForStringIdEntry(char* cred_id, char* name, char* value) { printf("  v1   field \"%s\"\n", s(name)); }
void matcher(void);
}

int main() {
    request = readFile("REQUEST");
    creds = readFile("CREDS");
    matcher();
    return 0;
}
