# Tailscale SOCKS5 & Remote LuCI Browser untuk Android

Developed & Maintained by **Lutfi Widianto** ([@lutfiwidianto](https://github.com/lutfiwidianto))

Aplikasi Android untuk menjalankan **Tailscale Userspace SOCKS5 Proxy** sekaligus menyediakan **Built-in Web Browser** untuk meremote web interface LuCI OpenWrt dari luar jaringan tanpa bentrok dengan aplikasi VPN Inject (Clash / FlClash / gatchaNG / NekoBox / v2rayNG).

---

## 🚀 Mengapa Menggunakan Aplikasi Ini?

Di Android, sistem operasi hanya mengizinkan **1 slot VPN aktif**. Jika Anda menggunakan VPN Inject (seperti gatchaNG atau FlClash), Tailscale aplikasi standar tidak bisa dinyalakan karena berebut slot VPN.

Dengan proyek ini:
1. **Tidak Memakan Slot VPN**: Tailscale berjalan di mode *userspace* dan mengeluarkan port SOCKS5 lokal (`127.0.0.1:1080`). Slot VPN Android tetap bebas digunakan oleh VPN Inject Anda.
2. **Built-in LuCI Web Browser**: Dilengkapi browser internal bawaan yang me-route seluruh trafiknya langsung ke SOCKS5 Tailscale, mengabaikan pembajakan trafik dari VPN Inject/Chrome sehingga Anda bisa meremote web LuCI OpenWrt (`https://100.x.y.z/cgi-bin/luci/`) dengan 100% lancar.
3. **Simpan Alamat Router Kustom**: Alamat URL LuCI rumah Anda dapat disimpan secara permanen di aplikasi.
4. **Auto-Update In-App**: Mengecek rilis terbaru di GitHub secara otomatis dan mengunduh + menginstal APK langsung dari dalam aplikasi.

---

## ✨ Fitur Utama

- 🌐 **SOCKS5 Proxy Local** (`127.0.0.1:1080`): Mendukung TCP & UDP forwarding untuk rute Tailscale (`100.64.0.0/10` dan subnet LAN).
- 🌐 **Browser Internal Bawaan (LuCI Remote)**: Buka tampilan Web LuCI OpenWrt tanpa perlu browser eksternal.
- ⚡ **Self-Signed SSL Bypass**: Penanganan otomatis sertifikat HTTPS self-signed pada OpenWrt.
- 💾 **Penyimpanan Alamat Router**: Simpan URL default LuCI Anda (contoh: `https://100.73.70.18/cgi-bin/luci/`).
- 🔄 **In-App Auto Update**: Pembaruan otomatis APK langsung dari GitHub Releases.
- 🔋 **Pengaturan Background Keep-Alive**: Bypass optimasi baterai dan notifikasi persisten agar service tetap aktif di latar belakang.
- 📝 **Live Logs**: Tampilan log realtime Tailscale dan log aplikasi.

---

## 📥 Cara Instalasi

1. Unduh APK rilis terbaru dari halaman [Releases](../../releases).
2. Buka aplikasi, lalu tekan **Mulai**.
3. Jika pertama kali menginstal, tekan **Buka Link Login** untuk melakukan otorisasi perangkat Tailscale Anda di browser.
4. Tekan tombol **Buka Web Router (LuCI)** untuk langsung meremote OpenWrt Anda!

---

## 📜 FlClash / Clash Overwrite Script

Jika Anda ingin mengarahkan trafik Tailscale dari aplikasi lain melalui Clash/FlClash, gunakan script overwrite berikut:

*Masuk ke menu FlClash: **Tools -> Advanced Config -> Script -> Tambah**:*

```js
const main = (config) => {
  config.proxies = (config.proxies || []).filter(p => p.name !== "tailscale");
  config.proxies.unshift({
    name: "tailscale", type: "socks5",
    server: "127.0.0.1", port: 1080, udp: true,
  });

  let g = (config["proxy-groups"] || []).find(g => g.name === "Tailscale");
  if (!g) {
    g = { name: "Tailscale", type: "select", proxies: ["tailscale", "DIRECT"] };
    config["proxy-groups"].push(g);
  } else if (!g.proxies.includes("tailscale")) {
    g.proxies.unshift("tailscale");
  }

  const rules = [
    "DOMAIN-SUFFIX,derp.tailscale.com,Tailscale",
    "DOMAIN-SUFFIX,ts.net,Tailscale",
    "IP-CIDR,100.64.0.0/10,Tailscale,no-resolve",
    "IP-CIDR,192.168.1.0/24,Tailscale,no-resolve",
    "IP-CIDR,fd7a:115c:a1e0::/48,Tailscale,no-resolve",
  ];
  const existing = new Set(config.rules.map(r => r.trim()));
  for (const r of rules.filter(r => !existing.has(r)).reverse()) {
    config.rules.unshift(r);
  }

  config.dns ??= {};
  config.dns["fake-ip-filter"] ??= [];
  for (const f of ["DOMAIN-SUFFIX,ts.net", "DOMAIN-SUFFIX,derp.tailscale.com"]) {
    if (!config.dns["fake-ip-filter"].includes(f)) {
      config.dns["fake-ip-filter"].push(f);
    }
  }

  return config;
};
```

---

## 🏗️ Cara Kerja Arsitektur

```text
HP Android (VPN Slot dipakai gatchaNG / Clash)
  │
  ├─ Browser Bawaan App / Aplikasi → SOCKS5 Proxy (127.0.0.1:1080)
  │                                        │
  │                                 ts-proxy (Userspace)
  │                                        │
  └───────────────────────────────> Router OpenWrt LuCI (100.x.y.z)
```

---

## 🛠️ Kompilasi dari Source

### Persyaratan:
- Go 1.24+
- Android NDK (r26+)
- `gomobile`

```bash
# Install gomobile
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

# Clone repository
git clone https://github.com/lutfiwidianto/tailscale-socks5-id.git
cd tailscale-socks5-id

# Build AAR
gomobile init
gomobile bind -ldflags="-checklinkname=0 -s -w" -target=android -androidapi=26 -o android-app/app/libs/tsproxy.aar ./mobile/

# Build APK
cd android-app
./gradlew assembleRelease
```

---

## 🙏 Kredit & Pengembang

- **Pengembang Utama**: [Lutfi Widianto](https://github.com/lutfiwidianto)
- Based on original concept by [0xKrito/tailscale-socks5-Android](https://github.com/0xKrito/tailscale-socks5-Android) & [ge9/ts-proxy](https://github.com/ge9/ts-proxy)
- [txthinking/socks5](https://github.com/txthinking/socks5)
- [wlynxg/anet](https://github.com/wlynxg/anet)
- [Tailscale](https://github.com/tailscale/tailscale)

---

## 📄 Lisensi

Proyek ini dilisensikan di bawah [BSD-3-Clause License](LICENSE).
