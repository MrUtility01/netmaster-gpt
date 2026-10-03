package com.example.netmaster.domain

/** راهنمای عملی VLAN، تانل، LB، Failover، محدودیت حجم — Cisco و MikroTik */
data class OpsGuideTopic(
    val id: String,
    val title: String,
    val summary: String,
    val cisco: String,
    val mikrotik: String,
    val steps: List<String>
)

object NetworkOpsGuide {

    fun all(): List<OpsGuideTopic> = listOf(
        OpsGuideTopic(
            id = "vlan-access",
            title = "VLAN Access (جداسازی کاربران)",
            summary = "هر گروه در یک VLAN جدا — مثلاً کاربران VLAN10، سرور VLAN20، Voice VLAN30. پورت کاربر = Access، پورت بین سوئیچ‌ها = Trunk.",
            cisco = """
! VLANهای مشترک در همه سوئیچ‌ها
vlan 10
 name USERS
vlan 20
 name SERVERS
vlan 30
 name VOICE
!
interface GigabitEthernet1/0/10
 switchport mode access
 switchport access vlan 10
 spanning-tree portfast
!
interface GigabitEthernet1/0/1
 switchport mode trunk
 switchport trunk allowed vlan 10,20,30
 switchport trunk native vlan 999
""".trimIndent(),
            mikrotik = """
/interface bridge add name=BR1 vlan-filtering=yes
/interface bridge port add bridge=BR1 interface=ether2 pvid=10
/interface bridge port add bridge=BR1 interface=ether3 pvid=20
/interface bridge vlan add bridge=BR1 tagged=ether1 untagged=ether2 vlan-ids=10
/interface bridge vlan add bridge=BR1 tagged=ether1 untagged=ether3 vlan-ids=20
/interface vlan add name=vlan10 interface=BR1 vlan-id=10
/ip address add address=192.168.10.1/24 interface=vlan10
""".trimIndent(),
            steps = listOf(
                "VLAN را روی همه سوئیچ‌های مسیر تعریف کن (VLAN مشترک = همان ID همه جا)",
                "پورت کاربر: Access + PVID همان VLAN",
                "لینک بین سوئیچ/روتر: Trunk و allowed VLANها را محدود کن",
                "روی روتر/L3: SVI یا VLAN interface + IP gateway",
                "تست: ping بین دو host هم‌VLAN باید OK باشد؛ بین‌VLAN نیاز به مسیریابی دارد"
            )
        ),
        OpsGuideTopic(
            id = "vlan-shared",
            title = "VLAN مشترک (Shared / Management)",
            summary = "یک VLAN برای مدیریت همه دستگاه‌ها (مثلاً VLAN 99) تا snmp/ssh از یک subnet انجام شود. Native VLAN جدا از کاربران باشد.",
            cisco = """
vlan 99
 name MGMT
interface Vlan99
 ip address 10.99.0.2 255.255.255.0
!
interface GigabitEthernet1/0/1
 switchport trunk native vlan 99
 switchport trunk allowed vlan 10,20,30,99
""".trimIndent(),
            mikrotik = """
/interface vlan add name=mgmt interface=BR1 vlan-id=99
/ip address add address=10.99.0.2/24 interface=mgmt
/ip service set ssh address=10.99.0.0/24
""".trimIndent(),
            steps = listOf(
                "VLAN مدیریت را از VLAN کاربران جدا کن",
                "فقط پورت‌های مدیریتی/Trunk این VLAN را حمل کنند",
                "ACL: فقط از شبکه مدیریت به SSH/Winbox اجازه بده",
                "Native VLAN را برابر VLAN مدیریت نگذار روی لینک‌های عمومی (امنیت)"
            )
        ),
        OpsGuideTopic(
            id = "vlan-voice",
            title = "VLAN Voice + Data روی یک پورت",
            summary = "تلفن IP روی Voice VLAN و PC پشت تلفن روی Data VLAN — Cisco: voice vlan؛ MikroTik: PVID + tagged.",
            cisco = """
interface GigabitEthernet1/0/15
 switchport mode access
 switchport access vlan 10
 switchport voice vlan 30
 spanning-tree portfast
 mls qos trust dscp
""".trimIndent(),
            mikrotik = """
# ether5: data untagged VLAN10، voice tagged VLAN30
/interface bridge port add bridge=BR1 interface=ether5 pvid=10
/interface bridge vlan add bridge=BR1 tagged=ether5,ether1 untagged=ether5 vlan-ids=10
/interface bridge vlan add bridge=BR1 tagged=ether5,ether1 vlan-ids=30
""".trimIndent(),
            steps = listOf(
                "Voice VLAN جدا + QoS/DSCP",
                "DHCP جدا برای تلفن‌ها یا Option 156/VoIP",
                "تست: تلفن IP می‌گیرد از VLAN30، PC از VLAN10"
            )
        ),
        OpsGuideTopic(
            id = "tunnel-gre",
            title = "تونل GRE / IPIP (سایت به سایت)",
            summary = "دو سایت را با تونل نقطه‌به‌نقطه وصل کن. برای رمزنگاری روی GRE از IPsec استفاده کن.",
            cisco = """
interface Tunnel0
 ip address 172.16.0.1 255.255.255.252
 tunnel source GigabitEthernet0/0
 tunnel destination 203.0.113.2
 tunnel mode gre ip
!
ip route 192.168.20.0 255.255.255.0 Tunnel0
! با IPsec (ساده):
crypto ipsec profile SITE
 set transform-set AES_SHA
interface Tunnel0
 tunnel protection ipsec profile SITE
""".trimIndent(),
            mikrotik = """
/interface gre add name=gre-to-hq local-address=203.0.113.1 remote-address=203.0.113.2
/ip address add address=172.16.0.1/30 interface=gre-to-hq
/ip route add dst-address=192.168.20.0/24 gateway=gre-to-hq
# IPsec برای رمز کردن GRE:
/ip ipsec peer add address=203.0.113.2/32 exchange-mode=ike2 name=hq
/ip ipsec identity add peer=hq secret=StrongSecret
/ip ipsec policy add src-address=0.0.0.0/0 dst-address=0.0.0.0/0 tunnel=yes peer=hq
""".trimIndent(),
            steps = listOf(
                "آدرس public دو طرف و reachability (ping) را چک کن",
                "تونل را بساز و IP لینک داخلی بده",
                "Route شبکه‌های remote را از تونل بفرست",
                "اختیاری: IPsec برای رمزنگاری",
                "تست: ping از LAN-A به LAN-B"
            )
        ),
        OpsGuideTopic(
            id = "tunnel-wireguard",
            title = "تونل WireGuard (میکروتیک) / DMVPN نکته سیسکو",
            summary = "WireGuard سبک و سریع برای سایت/کاربر راه‌دور. در سیسکو اغلب FlexVPN/DMVPN+IPsec استفاده می‌شود.",
            cisco = """
! FlexVPN client lab profile (safe/read-only reference)
crypto ikev2 proposal P1
 encryption aes-gcm-256
 group 19
crypto ikev2 profile FLEX
 match identity remote any
 authentication local pre-share
 authentication remote pre-share
 keyring local KEYS
interface Tunnel1
 ip address negotiated
 tunnel protection ipsec profile FLEX_PROF
""".trimIndent(),
            mikrotik = """
/interface wireguard add name=wg1 listen-port=51820 private-key="<LOCAL_PRIVATE>"
/ip address add address=10.10.10.1/24 interface=wg1
/interface wireguard peers add interface=wg1 public-key="<REMOTE_PUBLIC>" \
  endpoint-address=203.0.113.2 endpoint-port=51820 allowed-address=192.168.20.0/24,10.10.10.2/32
/ip route add dst-address=192.168.20.0/24 gateway=wg1
""".trimIndent(),
            steps = listOf(
                "کلید عمومی/خصوصی دو طرف را جابه‌جا کن",
                "allowed-address را دقیق بگذار (least privilege)",
                "فایروال UDP پورت WG را باز کن",
                "Keepalive برای NATTraversal"
            )
        ),
        OpsGuideTopic(
            id = "load-balance",
            title = "لودبالانس دو لینک اینترنت",
            summary = "ترافیک را بین دو WAN پخش کن. PCC یا ECMP. مراقب sessionهای HTTPS باش (per-connection نه per-packet کور).",
            cisco = """
! دو default route با track
ip route 0.0.0.0 0.0.0.0 203.0.113.1 track 1
ip route 0.0.0.0 0.0.0.0 198.51.100.1 10 track 2
! یا PBR / zone-based — برای LB واقعی اغلب از IOS-XE ECMP:
ip route 0.0.0.0 0.0.0.0 203.0.113.1
ip route 0.0.0.0 0.0.0.0 198.51.100.1
""".trimIndent(),
            mikrotik = """
/ip route add dst-address=0.0.0.0/0 gateway=203.0.113.1 distance=1 check-gateway=ping
/ip route add dst-address=0.0.0.0/0 gateway=198.51.100.1 distance=1 check-gateway=ping
# PCC load balance:
/ip firewall mangle add chain=prerouting dst-address-type=!local in-interface=LAN \
  per-connection-classifier=both-addresses-and-ports:2/0 action=mark-connection new-connection-mark=wan1
/ip firewall mangle add chain=prerouting dst-address-type=!local in-interface=LAN \
  per-connection-classifier=both-addresses-and-ports:2/1 action=mark-connection new-connection-mark=wan2
/ip firewall mangle add chain=prerouting connection-mark=wan1 action=mark-routing new-routing-mark=to-wan1
/ip firewall mangle add chain=prerouting connection-mark=wan2 action=mark-routing new-routing-mark=to-wan2
/ip route add dst-address=0.0.0.0/0 gateway=203.0.113.1 routing-mark=to-wan1
/ip route add dst-address=0.0.0.0/0 gateway=198.51.100.1 routing-mark=to-wan2
""".trimIndent(),
            steps = listOf(
                "دو gateway و NAT برای هر WAN",
                "mark connection پایدار (PCC) تا یک session از یک لینک برود",
                "مانیتور: /tool torch یا NetFlow",
                "تست قطع یکی از لینک‌ها"
            )
        ),
        OpsGuideTopic(
            id = "failover",
            title = "Failover لینک (پشتیبان خودکار)",
            summary = "لینک اصلی با distance کمتر؛ اگر ping قطع شد، route پشتیبان فعال می‌شود.",
            cisco = """
ip sla 1
 icmp-echo 8.8.8.8 source-interface GigabitEthernet0/0
 frequency 5
ip sla schedule 1 life forever start-time now
track 1 ip sla 1 reachability
ip route 0.0.0.0 0.0.0.0 203.0.113.1 track 1
ip route 0.0.0.0 0.0.0.0 198.51.100.1 20
""".trimIndent(),
            mikrotik = """
/ip route add dst-address=0.0.0.0/0 gateway=203.0.113.1 distance=1 check-gateway=ping
/ip route add dst-address=0.0.0.0/0 gateway=198.51.100.1 distance=10 check-gateway=ping
# یا Netwatch:
/tool netwatch add host=8.8.8.8 interval=10s \
  down-script="/ip route set [find comment=PRIMARY] disabled=yes" \
  up-script="/ip route set [find comment=PRIMARY] disabled=no"
""".trimIndent(),
            steps = listOf(
                "Route اصلی distance پایین‌تر",
                "چک gateway با ping یا IP SLA",
                "NAT و DNS را برای هر دو WAN آماده کن",
                "زمان failover را با interval تست کن"
            )
        ),
        OpsGuideTopic(
            id = "quota",
            title = "محدودیت حجم / سرعت کاربران",
            summary = "Rate-limit و quota حجم. میکروتیک: Simple Queue / Queue Tree + User Manager یا script. سیسکو: policing/shaping روی class-map.",
            cisco = """
class-map match-all USER_A
 match access-group name ACL_USER_A
policy-map LIMIT_5M
 class USER_A
  police cir 5000000 bc 93750 conform-action transmit exceed-action drop
interface GigabitEthernet0/1
 service-policy input LIMIT_5M
! برای shaping خروجی:
policy-map SHAPE_10M
 class class-default
  shape average 10000000
""".trimIndent(),
            mikrotik = """
# محدودیت سرعت 5M/2M برای یک IP
/queue simple add name=user50 target=192.168.10.50/32 max-limit=5M/2M
# محدودیت حجم ماهانه تقریبی با script + hotspot/user-manager:
/ip hotspot user profile add name=2GB rate-limit=3M/1M
/ip hotspot user add name=ali password=xxx profile=2GB limit-bytes-total=2147483648
# یا Queue Tree + PCQ برای fairness بین کاربران:
/queue type add name=pcq-down kind=pcq pcq-rate=2M pcq-classifier=dst-address
/queue tree add name=down parent=global queue=pcq-down max-limit=50M
""".trimIndent(),
            steps = listOf(
                "هدف: per-IP یا per-user یا per-VLAN",
                "max-limit برای سقف سرعت؛ limit-bytes برای سقف حجم",
                "بعد از اتمام حجم: redirect به صفحه شارژ یا قطع",
                "لاگ مصرف را نگه دار برای پشتیبانی"
            )
        )
    )
}
