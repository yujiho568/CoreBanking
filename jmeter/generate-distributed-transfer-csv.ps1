param(
    [int] $AccountCount = 1000,
    [int] $Rows = 50000,
    [int] $AmountMin = 100,
    [int] $AmountMax = 1000,
    [int] $AmountStep = 100,
    [int] $Seed = 20260707,
    [string] $OutputPath = "jmeter/distributed-transfer-requests.csv"
)

if ($AccountCount -lt 2) {
    throw "AccountCount must be at least 2."
}

if ($Rows -lt 1) {
    throw "Rows must be at least 1."
}

if ($AmountMin -lt 1 -or $AmountMax -lt $AmountMin -or $AmountStep -lt 1) {
    throw "Invalid amount range."
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
}

$random = [System.Random]::new($Seed)
$writer = [System.IO.StreamWriter]::new($OutputPath, $false, [System.Text.UTF8Encoding]::new($false))

try {
    $writer.WriteLine("fromAccountId,toAccountId,amount")

    for ($i = 0; $i -lt $Rows; $i++) {
        $fromIndex = $random.Next(1, $AccountCount + 1)
        do {
            $toIndex = $random.Next(1, $AccountCount + 1)
        } while ($toIndex -eq $fromIndex)

        $steps = [math]::Floor(($AmountMax - $AmountMin) / $AmountStep)
        $amount = $AmountMin + ($random.Next(0, $steps + 1) * $AmountStep)

        $fromAccountId = "PHASEB-ACC-{0:D6}" -f $fromIndex
        $toAccountId = "PHASEB-ACC-{0:D6}" -f $toIndex
        $line = "{0},{1},{2}.00" -f $fromAccountId, $toAccountId, $amount
        $writer.WriteLine($line)
    }
}
finally {
    $writer.Close()
}

Write-Host "Generated $Rows distributed transfer rows at $OutputPath"
