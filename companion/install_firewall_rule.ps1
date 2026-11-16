# Adds Windows Firewall inbound rules for DeckVault Companion on Private networks
$rules = @(
    @{ Name = "DeckVault Companion HTTP"; Protocol = "TCP"; Port = 48720 },
    @{ Name = "DeckVault Companion Discovery"; Protocol = "UDP"; Port = 48721 }
)

foreach ($r in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Remove-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    }
    New-NetFirewallRule -DisplayName $r.Name `
        -Direction Inbound `
        -Action Allow `
        -Protocol $r.Protocol `
        -LocalPort $r.Port `
        -Profile Private,Domain | Out-Null
    Write-Host "Added firewall rule: $($r.Name) ($($r.Protocol) $($r.Port))"
}
Write-Host "Done! Your Samsung Tab S8 Ultra and Z Fold 5 can now connect over Wi-Fi."
Start-Sleep -Seconds 3
