#!/bin/bash

# 秒杀系统Docker环境启动脚本

echo "=========================================="
echo "🚀 启动秒杀系统依赖服务"
echo "=========================================="

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker未运行，请先启动Docker"
    exit 1
fi

# 检查docker-compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose未安装，请先安装docker-compose"
    exit 1
fi

# 停止并删除现有容器
echo "🧹 清理现有容器..."
docker-compose down -v

# 拉取最新镜像
echo "📥 拉取Docker镜像..."
docker-compose pull

# 启动服务
echo "🔄 启动服务..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动完成..."
sleep 30

# 检查服务状态
echo "📊 检查服务状态..."
docker-compose ps

# 检查MySQL连接
echo "🔍 检查MySQL连接..."
for i in {1..30}; do
    if docker exec seckill-mysql mysqladmin ping -h localhost -u root -p123456 --silent; then
        echo "✅ MySQL已就绪"
        break
    fi
    echo "⏳ 等待MySQL启动... ($i/30)"
    sleep 2
done

# 检查Redis连接
echo "🔍 检查Redis连接..."
if docker exec seckill-redis redis-cli ping > /dev/null 2>&1; then
    echo "✅ Redis已就绪"
else
    echo "❌ Redis连接失败"
fi

# 检查RabbitMQ连接
echo "🔍 检查RabbitMQ连接..."
for i in {1..30}; do
    if docker exec seckill-rabbitmq rabbitmq-diagnostics ping > /dev/null 2>&1; then
        echo "✅ RabbitMQ已就绪"
        break
    fi
    echo "⏳ 等待RabbitMQ启动... ($i/30)"
    sleep 2
done

echo "=========================================="
echo "🎉 服务启动完成！"
echo "=========================================="
echo "📊 服务访问地址："
echo "  MySQL:         localhost:3306"
echo "  Redis:         localhost:6379"
echo "  RabbitMQ:      localhost:5672"
echo "  RabbitMQ管理:  http://localhost:15672 (guest/guest)"
echo "  Redis管理:     http://localhost:8081"
echo "=========================================="
echo "💡 使用以下命令查看日志："
echo "  docker-compose logs -f [service_name]"
echo "💡 使用以下命令停止服务："
echo "  docker-compose down"
echo "=========================================="
