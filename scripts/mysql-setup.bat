@echo off
REM stock-signal 本地 MySQL 初始化脚本 —— 需要【以管理员身份运行】一次
REM 作用：初始化数据目录、注册并启动 MySQL84 服务、建库建表（docs/db/schema.sql）、创建应用账号 stock
setlocal

set "MYSQL_HOME=C:\Program Files\MySQL\MySQL Server 8.4"
set "MYSQL_DATA=C:\ProgramData\MySQL\MySQL Server 8.4"
set "SCHEMA=%~dp0..\docs\db\schema.sql"

if not exist "%MYSQL_HOME%\bin\mysqld.exe" (
    echo [ERROR] 未找到 %MYSQL_HOME%\bin\mysqld.exe，请确认 MySQL 安装路径
    pause & exit /b 1
)

echo [1/5] 初始化数据目录（root 空密码，仅本机开发用）...
mkdir "%MYSQL_DATA%" 2>nul
"%MYSQL_HOME%\bin\mysqld.exe" --initialize-insecure --datadir="%MYSQL_DATA%\Data"
if errorlevel 1 ( echo [ERROR] 初始化失败 & pause & exit /b 1 )

echo [2/5] 写入 my.ini ...
> "%MYSQL_DATA%\my.ini" echo [mysqld]
>> "%MYSQL_DATA%\my.ini" echo datadir=%MYSQL_DATA:\=\\%\\Data
>> "%MYSQL_DATA%\my.ini" echo port=3306
>> "%MYSQL_DATA%\my.ini" echo character-set-server=utf8mb4
>> "%MYSQL_DATA%\my.ini" echo bind-address=127.0.0.1

echo [3/5] 注册并启动 MySQL84 服务...
"%MYSQL_HOME%\bin\mysqld.exe" --install MySQL84 --defaults-file="%MYSQL_DATA%\my.ini"
net start MySQL84
if errorlevel 1 ( echo [ERROR] 服务启动失败 & pause & exit /b 1 )

echo [4/5] 建库建表...
"%MYSQL_HOME%\bin\mysql.exe" -u root --skip-password < "%SCHEMA%"
if errorlevel 1 ( echo [ERROR] schema 导入失败 & pause & exit /b 1 )

echo [5/5] 创建应用账号 stock / stock（仅 localhost，与 application.yml 默认值一致）...
"%MYSQL_HOME%\bin\mysql.exe" -u root --skip-password -e "CREATE USER IF NOT EXISTS 'stock'@'localhost' IDENTIFIED BY 'stock'; GRANT ALL PRIVILEGES ON stock_signal.* TO 'stock'@'localhost'; FLUSH PRIVILEGES;"

echo.
echo 完成。root 当前为空密码且仅监听 127.0.0.1，本机开发够用；如需设 root 密码：
echo   mysql -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '你的密码';"
pause
