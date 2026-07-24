$bin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin'
$p = [Environment]::GetEnvironmentVariable('Path', 'User')
if ($null -eq $p) { $p = '' }
if ($p -notlike "*$bin*") {
    [Environment]::SetEnvironmentVariable('Path', ($p.TrimEnd(';') + ';' + $bin), 'User')
    Write-Output 'PATH updated: added MySQL bin'
} else {
    Write-Output 'PATH already contains MySQL bin'
}
