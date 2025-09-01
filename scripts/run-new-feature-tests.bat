@echo off
echo 🚀 新功能集成测试执行脚本
echo ====================================

echo 📋 步骤1: 检查环境和服务状态
echo ------------------------------------

REM 检查Docker是否运行
docker version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker 未运行，请先启动 Docker
    pause
    exit /b 1
)
echo ✅ Docker 服务正常

REM 检查Maven
mvn --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Maven 未找到，请确保 Maven 已安装
    pause
    exit /b 1
)
echo ✅ Maven 环境正常

echo.
echo 📋 步骤2: 启动必要的服务
echo ------------------------------------

REM 启动Docker服务
echo 🐳 启动 Docker 服务...
call scripts\docker-setup.sh
if errorlevel 1 (
    echo ❌ Docker 服务启动失败
    pause
    exit /b 1
)

REM 等待服务完全启动
echo ⏳ 等待服务完全启动...
timeout /t 15 /nobreak >nul

REM 验证服务状态
echo 🔍 验证服务状态...
docker ps --format "table {{.Names}}\t{{.Status}}" | findstr /C:"redis" /C:"mysql" /C:"rabbitmq"

echo.
echo 📋 步骤3: 执行测试套件
echo ------------------------------------

echo 🧪 开始执行新功能集成测试...
mvn clean test -Dtest=NewFeatureIntegrationTest -q

if errorlevel 1 (
    echo.
    echo ❌ 测试执行失败！
    echo 💡 建议检查：
    echo    1. 确保所有服务正常运行
    echo    2. 检查应用程序是否正常启动
    echo    3. 查看详细测试日志
    echo.
    echo 🔧 调试命令：
    echo    mvn test -Dtest=NewFeatureIntegrationTest -X
    pause
    exit /b 1
) else (
    echo.
    echo ✅ 测试执行成功！
    echo 🎉 所有新功能验证通过！
)

echo.
echo 📋 步骤4: 生成测试报告
echo ------------------------------------

if exist "target\surefire-reports\TEST-*.xml" (
    echo 📊 测试报告已生成：target\surefire-reports\
    echo 💡 可以查看详细测试结果
) else (
    echo ⚠️ 未找到测试报告文件
)

echo.
echo 🎯 测试完成总结
echo ====================================
echo ✅ 环境检查完成
echo ✅ 服务启动完成  
echo ✅ 测试执行完成
echo 🏆 新功能集成测试全部通过！
echo.
echo 📚 更多信息请查看：新功能测试指南.md
echo.

pause
