#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
秒杀系统并发测试脚本

功能：
1. 模拟多用户并发抢购
2. 统计成功/失败率
3. 验证数据一致性
4. 性能监控

使用方法：
python concurrent-test.py --users 1000 --voucher-id 1 --limit 1
"""

import requests
import threading
import time
import json
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import defaultdict
import statistics

# 全局配置
BASE_URL = "http://localhost:8080/api/seckill"
results = defaultdict(int)
response_times = []
errors = []
success_orders = []
lock = threading.Lock()

def seckill_request(user_id, voucher_id, limit=1):
    """发送秒杀请求"""
    start_time = time.time()
    
    try:
        payload = {
            "userId": user_id,
            "voucherId": voucher_id,
            "limit": limit
        }
        
        response = requests.post(
            BASE_URL,
            json=payload,
            timeout=10,
            headers={"Content-Type": "application/json"}
        )
        
        end_time = time.time()
        response_time = (end_time - start_time) * 1000  # 转换为毫秒
        
        with lock:
            response_times.append(response_time)
            
            if response.status_code == 200:
                data = response.json()
                result_code = data.get('code', -1)
                
                if result_code == 0:  # 成功
                    results['success'] += 1
                    success_orders.append(data.get('orderId'))
                    print(f"✅ 用户 {user_id} 秒杀成功，订单: {data.get('orderId')}")
                elif result_code == 1:  # 库存不足
                    results['stock_empty'] += 1
                    print(f"❌ 用户 {user_id} 秒杀失败：库存不足")
                elif result_code == 2:  # 超过限购
                    results['limit_exceeded'] += 1
                    print(f"❌ 用户 {user_id} 秒杀失败：超过限购")
                else:  # 其他错误
                    results['other_error'] += 1
                    print(f"❌ 用户 {user_id} 秒杀失败：{data.get('message', '未知错误')}")
            else:
                results['http_error'] += 1
                errors.append(f"用户 {user_id}: HTTP {response.status_code}")
                print(f"❌ 用户 {user_id} 请求失败：HTTP {response.status_code}")
                
    except requests.exceptions.Timeout:
        with lock:
            results['timeout'] += 1
            errors.append(f"用户 {user_id}: 请求超时")
        print(f"⏰ 用户 {user_id} 请求超时")
        
    except requests.exceptions.RequestException as e:
        with lock:
            results['network_error'] += 1
            errors.append(f"用户 {user_id}: 网络错误 - {str(e)}")
        print(f"🌐 用户 {user_id} 网络错误：{str(e)}")
        
    except Exception as e:
        with lock:
            results['unknown_error'] += 1
            errors.append(f"用户 {user_id}: 未知错误 - {str(e)}")
        print(f"💥 用户 {user_id} 未知错误：{str(e)}")

def check_stock_before_test(voucher_id):
    """测试前检查库存"""
    try:
        response = requests.get(f"{BASE_URL}/stock/{voucher_id}", timeout=5)
        if response.status_code == 200:
            data = response.json()
            return data.get('currentStock', 0)
    except Exception as e:
        print(f"⚠️ 检查库存失败：{e}")
    return 0

def check_stock_after_test(voucher_id):
    """测试后检查库存"""
    return check_stock_before_test(voucher_id)

def check_order_status(order_ids, max_wait_time=60):
    """检查订单状态"""
    print(f"\n🔍 检查 {len(order_ids)} 个订单状态...")
    
    success_count = 0
    processing_count = 0
    failed_count = 0
    
    for order_id in order_ids:
        try:
            response = requests.get(f"{BASE_URL}/status/{order_id}", timeout=5)
            if response.status_code == 200:
                data = response.json()
                status = data.get('status')
                
                if status == 1:  # 成功
                    success_count += 1
                elif status == 0:  # 处理中
                    processing_count += 1
                elif status == 2:  # 失败
                    failed_count += 1
                    
        except Exception as e:
            print(f"⚠️ 检查订单 {order_id} 状态失败：{e}")
    
    print(f"📊 订单状态统计：")
    print(f"  ✅ 成功：{success_count}")
    print(f"  ⏳ 处理中：{processing_count}")
    print(f"  ❌ 失败：{failed_count}")
    
    return success_count, processing_count, failed_count

def print_statistics():
    """打印统计信息"""
    total_requests = sum(results.values())
    
    print("\n" + "="*60)
    print("📊 测试结果统计")
    print("="*60)
    
    print(f"📝 总请求数：{total_requests}")
    print(f"✅ 秒杀成功：{results['success']} ({results['success']/total_requests*100:.2f}%)")
    print(f"📦 库存不足：{results['stock_empty']} ({results['stock_empty']/total_requests*100:.2f}%)")
    print(f"🚫 超过限购：{results['limit_exceeded']} ({results['limit_exceeded']/total_requests*100:.2f}%)")
    print(f"❌ HTTP错误：{results['http_error']} ({results['http_error']/total_requests*100:.2f}%)")
    print(f"⏰ 请求超时：{results['timeout']} ({results['timeout']/total_requests*100:.2f}%)")
    print(f"🌐 网络错误：{results['network_error']} ({results['network_error']/total_requests*100:.2f}%)")
    print(f"💥 其他错误：{results['other_error'] + results['unknown_error']} ({(results['other_error'] + results['unknown_error'])/total_requests*100:.2f}%)")
    
    if response_times:
        print(f"\n⏱️ 响应时间统计（毫秒）：")
        print(f"  平均响应时间：{statistics.mean(response_times):.2f}")
        print(f"  最小响应时间：{min(response_times):.2f}")
        print(f"  最大响应时间：{max(response_times):.2f}")
        print(f"  中位数响应时间：{statistics.median(response_times):.2f}")
        if len(response_times) > 1:
            print(f"  响应时间标准差：{statistics.stdev(response_times):.2f}")
    
    if errors:
        print(f"\n❌ 错误详情（前10个）：")
        for error in errors[:10]:
            print(f"  {error}")
        if len(errors) > 10:
            print(f"  ... 还有 {len(errors) - 10} 个错误")

def main():
    parser = argparse.ArgumentParser(description='秒杀系统并发测试')
    parser.add_argument('--users', type=int, default=1000, help='并发用户数')
    parser.add_argument('--voucher-id', type=int, default=1, help='优惠券ID')
    parser.add_argument('--limit', type=int, default=1, help='限购数量')
    parser.add_argument('--max-workers', type=int, default=50, help='最大线程数')
    
    args = parser.parse_args()
    
    print("🚀 秒杀系统并发测试开始")
    print(f"👥 并发用户数：{args.users}")
    print(f"🎫 优惠券ID：{args.voucher_id}")
    print(f"🔢 限购数量：{args.limit}")
    print(f"🧵 最大线程数：{args.max_workers}")
    
    # 检查测试前库存
    initial_stock = check_stock_before_test(args.voucher_id)
    print(f"📦 测试前库存：{initial_stock}")
    
    if initial_stock <= 0:
        print("❌ 库存不足，无法进行测试")
        return
    
    # 开始并发测试
    start_time = time.time()
    
    with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        futures = []
        
        for user_id in range(1, args.users + 1):
            future = executor.submit(seckill_request, user_id, args.voucher_id, args.limit)
            futures.append(future)
        
        # 等待所有请求完成
        for future in as_completed(futures):
            future.result()
    
    end_time = time.time()
    total_time = end_time - start_time
    
    print(f"\n⏰ 总耗时：{total_time:.2f} 秒")
    print(f"🚀 平均QPS：{args.users / total_time:.2f}")
    
    # 检查测试后库存
    final_stock = check_stock_after_test(args.voucher_id)
    print(f"📦 测试后库存：{final_stock}")
    print(f"📈 库存变化：{initial_stock - final_stock}")
    
    # 验证数据一致性
    expected_stock_decrease = results['success']
    actual_stock_decrease = initial_stock - final_stock
    
    if expected_stock_decrease == actual_stock_decrease:
        print("✅ 数据一致性检查通过")
    else:
        print(f"❌ 数据一致性检查失败：预期减少 {expected_stock_decrease}，实际减少 {actual_stock_decrease}")
    
    # 检查订单状态
    if success_orders:
        time.sleep(5)  # 等待订单处理
        check_order_status(success_orders)
    
    # 打印统计信息
    print_statistics()

if __name__ == "__main__":
    main()
