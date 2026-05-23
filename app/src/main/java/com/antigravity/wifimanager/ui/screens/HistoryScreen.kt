package com.antigravity.wifimanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wifimanager.data.SwitchLog
import com.antigravity.wifimanager.ui.components.GlassCard
import com.antigravity.wifimanager.ui.theme.TextSecondary
import com.antigravity.wifimanager.ui.theme.WifiGood
import com.antigravity.wifimanager.ui.theme.WifiWeak

@Composable
fun HistoryScreen(
    historyLogs: List<SwitchLog>,
    isClearingHistory: Boolean = false,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Tiêu đề và nút xóa lịch sử
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lịch sử tối ưu",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nhật ký chuyển mạng tự động của thiết bị",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
            
            if (historyLogs.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    enabled = !isClearingHistory
                ) {
                    if (isClearingHistory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa lịch sử",
                            tint = MaterialTheme.colorScheme.error
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Chưa có lịch sử chuyển mạng nào",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyLogs) { log ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.timestamp,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = if (log.isSuccess) "Gợi ý thành công" else "Thất bại",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.isSuccess) WifiGood else WifiWeak
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Mạng cũ
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.fromSsid,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Tín hiệu cũ: ${log.fromSignal}%",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                // Mạng mới đề xuất
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = log.toSsid,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Tín hiệu mới: ${log.toSignal}%",
                                        fontSize = 11.sp,
                                        color = WifiGood,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (log.isSuccess && !log.connectionStatus.isNullOrBlank()) {
                                Text(
                                    text = "Trạng thái: ${log.connectionStatus}",
                                    fontSize = 11.sp,
                                    color = WifiGood,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!log.isSuccess && !log.failureReason.isNullOrBlank()) {
                                Text(
                                    text = "Lý do: ${log.failureReason}",
                                    fontSize = 11.sp,
                                    color = WifiWeak,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
