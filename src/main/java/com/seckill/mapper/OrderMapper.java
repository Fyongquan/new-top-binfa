package com.seckill.mapper;

import com.seckill.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单Mapper接口
 * 
 * @author seckill-test
 */
@Mapper
public interface OrderMapper {

  /**
   * 根据ID查询订单
   * 
   * @param id 订单ID
   * @return 订单信息
   */
  Order selectById(@Param("id") Long id);

  /**
   * 插入订单
   * 
   * @param order 订单信息
   * @return 影响行数
   */
  int insert(Order order);

  /**
   * 根据ID更新订单
   * 
   * @param order 订单信息
   * @return 影响行数
   */
  int updateById(Order order);

  /**
   * 根据ID删除订单
   * 
   * @param id 订单ID
   * @return 影响行数
   */
  int deleteById(@Param("id") Long id);

  /**
   * 根据用户ID和优惠券ID查询订单（用于幂等性检查）
   * 
   * @param userId    用户ID
   * @param voucherId 优惠券ID
   * @return 订单信息
   */
  Order findByUserIdAndVoucherId(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

  /**
   * 根据用户ID查询订单列表
   * 
   * @param userId 用户ID
   * @return 订单列表
   */
  List<Order> selectByUserId(@Param("userId") Long userId);

  /**
   * 根据优惠券ID查询订单列表
   * 
   * @param voucherId 优惠券ID
   * @return 订单列表
   */
  List<Order> selectByVoucherId(@Param("voucherId") Long voucherId);

  /**
   * 根据状态查询订单列表
   * 
   * @param status 订单状态
   * @return 订单列表
   */
  List<Order> selectByStatus(@Param("status") Integer status);

  /**
   * 统计用户在指定优惠券上的订单数量
   * 
   * @param userId    用户ID
   * @param voucherId 优惠券ID
   * @return 订单数量
   */
  int countByUserIdAndVoucherId(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

  /**
   * 批量更新订单状态
   * 
   * @param orderIds 订单ID列表
   * @param status   新状态
   * @return 影响行数
   */
  int batchUpdateStatus(@Param("orderIds") List<Long> orderIds, @Param("status") Integer status);
}
