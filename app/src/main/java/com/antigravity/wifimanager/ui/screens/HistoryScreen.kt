package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.SwitchLog
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.theme.*

@Composable
fun HistoryScreen(
    historyLogs: List<SwitchLog>,
    isClearingHistory: Boolean = false,
    onClearHistory: () -> Unit
) {
    val totalLogs = historyLogs.size
    val successLogs = historyLogs.count { it.isSuccess }
    val successRate = if (totalLogs > 0) (successLogs.toFloat() / totalLogs * 100).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Tiêu đề đầu trang và nút xóa lịch sử
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lịch sử tối ưu",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Nhật ký chuyển mạng tự động bảo vệ sóng nền",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            if (historyLogs.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    enabled = !isClearingHistory,
                    modifier = Modifier
                        .background(Color(0x0CEF4444), CircleShape)
                        .border(1.dp, Color(0x22EF4444), CircleShape)
                ) {
                    if (isClearingHistory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CyberRose
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa lịch sử",
                            tint = CyberRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historyLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0x0CFFFFFF), CircleShape)
                            .border(1.dp, Color(0x18FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "Chưa ghi nhận lịch sử chuyển đổi",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Hiển thị Thẻ phân tích hiệu năng cao cấp
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                containerColor = Color(0x0CFFFFFF)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PHÂN TÍCH HIỆU NĂNG TỰ ĐỘNG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Tổng tối ưu", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text(text = "$totalLogs lần", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        }
                        Column {
                            Text(text = "Thành công", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text(text = "$successLogs lần", fontSize = 18.sp, fontWeight = FontWeight.Black, color = CyberEmerald)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Tỷ lệ chuẩn", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Text(text = "$successRate%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = CyberCyan)
                        }
                    }
                    
                    LinearProgressIndicator(
                        progress = successRate / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyberEmerald,
                        trackColor = Color(0x15FFFFFF)
                    )
                }
            }

            // Danh sách nhật ký timeline
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyLogs) { log ->
                    val statusColor = if (log.isSuccess) CyberEmerald else CyberRose
                    
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color(0x0CFFFFFF),
                        leftIndicatorColor = statusColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), // Chừa 22dp start tránh đè vạch màu đứng
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header của log
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.timestamp,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = if (log.isSuccess) "THÀNH CÔNG" else "THẤT BẠI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Chuyển mạng: Nguồn -> Đích
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Mạng cũ
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.fromSsid.replace("\"", ""),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sóng cũ: ${log.fromSignal}%",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = CyberIndigo,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(16.dp)
                                )

                                // Mạng mới đề xuất
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = log.toSsid.replace("\"", ""),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sóng mới: ${log.toSignal}%",
                                        fontSize = 11.sp,
                                        color = CyberEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Trạng thái / Chi tiết lỗi
                            if (log.isSuccess && !log.connectionStatus.isNullOrBlank()) {
                                Text(
                                    text = "Kết quả: ${log.connectionStatus}",
                                    fontSize = 11.sp,
                                    color = CyberEmerald,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                            }

                            if (!log.isSuccess && !log.failureReason.isNullOrBlank()) {
                                Text(
                                    text = "Chi tiết: ${log.failureReason}",
                                    fontSize = 11.sp,
                                    color = CyberRose,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

