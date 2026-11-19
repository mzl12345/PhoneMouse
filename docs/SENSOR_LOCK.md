# 传感器核心：已锁定（勿改）

区间：`// SENSOR-CORE-BEGIN` 到 `// SENSOR-CORE-END`（MainActivity.java）

**SHA256**：`26FFA128E1FAC82AE9E422E8D3A1291FD57EA5BE7B902E53D10B9CBDBFC2FCC2`
（❗ 该值与任何版本的代码都不匹配，见下方「指纹修订记录」）

## 指纹修订记录（2026-11-19，v2.0 重写开工前）

原记录的 `26FFA128…FC2FCC2` **无法复现**。按区间实测（当前 `MainActivity.java` 第 463–630 行，168 行）：

| 归一化方式 | 实测 SHA256 |
|---|---|
| 行间 `\n` 连接 | `6748D4F8E4C0ADA631B7D4F397952D6E386FD583D91DDD496934C9EAEA9BFF67` |
| 行间 `\r\n` 连接 | `727048424700054B7D584E9A2D776DBF3DA2922A25F78E9BCB52DBE3057A1A0C` |

已确认：工作区代码与 `_releases\v1.6-final-locked-2026-11-19` 快照**逐文件完全一致**，
即代码从未漂移，**是这份文档的指纹记错了**。以实测值为准。

### 本文档代码片段与现码的已知差异（3 处）

下面贴出的代码是早期版本，现码在此基础上有 3 处增量，均**不影响算法语义**：

| # | 差异 | 现码 |
|---|---|---|
| 1 | 缺「定中（clutch）」早退块 | `handleGyro` 在取 rx/ry/rz 之前有 `if (clutch) { sH=sV=0; accX=accY=0; return; }` |
| 2 | 缺 `GYRO` 心跳日志 | 每 5 秒写一行 `bias/pose/mouse` 到 diag.log，仅日志 |
| 3 | 节流常数 | 现码是 `if (now - lastMoveSent < 16) return;`（下面片段写作 `6`）—— **16ms 才对**，即 60Hz 上限 |

**以源码为准，本文档仅供参考。** 任何改动后按上表用同一归一化方式重新计算并更新本记录。

## 演进记录（为什么这么写）

| 版本 | 做法 | 结果 |
|---|---|---|
| 初版 | 倾角映射速度 | 放平就狂飘 |
| v1 | 增量式 + 0.02 慢速 EMA，阈值 2°/s | 零偏 > 2°/s 时永不校准 |
| v2 | 30 帧平均，6°/s 阈值 | 平均值混入动作 → 假零偏 |
| v3 | 48 帧中位数，极差 < 3° | 手抖让极差超标 → 停止更新 → 温漂 |
| **v4（当前）** | **200 帧中位数 + P90-P10 < 8° 判定 + 3 点中值滤波** | 静止 20 秒 0 位移 |

## 关键点

1. 手抖是零均值的，中位数能抓到真实零偏
2. 用分位差而非极差判断“是否在动”，一两个尖峰不影响判定
3. 窗口 200 帧（约 2 秒），热漂移能被持续跟踪
4. 3 点中值滤波干掉单帧尖峰
5. 软启动门限 2.5–7°/s：手抖不动，快转全速

## 静止测试

手机放桌上，开空中鼠标，电脑端接收 20 秒 → 应为 0 个 `mv` 包。

## 完整代码

```java
    // ================= SENSOR-CORE-BEGIN =================
    // ⚠ 传感器核心（零偏补偿 / 死区 / 轴选择）已验证稳定：
    //   静止 12 秒 0 位移。改动前请先备份，并跑一次静止测试。
    //   详见 docs\SENSOR_LOCK.md
    private void startSensors() {
        Handler h = new Handler(sensorThread.getLooper());
        if (gyro != null) sensorManager.registerListener(this, gyro, SENSOR_RATE * 1000, h);
        Sensor pose = gravSensor != null ? gravSensor : accel;
        if (pose != null) sensorManager.registerListener(this, pose, SensorManager.SENSOR_DELAY_UI, h);
        lastGyroTs = 0;
        poseW = 0.5f;
    }

    private void stopSensors() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_GYROSCOPE) {
            handleGyro(event);
            return;
        }
        float gx = event.values[0], gy = event.values[1], gz = event.values[2];
        if (type == Sensor.TYPE_ACCELEROMETER) {
            final float a = 0.85f;
            gravity[0] = a * gravity[0] + (1 - a) * gx;
            gravity[1] = a * gravity[1] + (1 - a) * gy;
            gravity[2] = a * gravity[2] + (1 - a) * gz;
            gx = gravity[0];
            gy = gravity[1];
            gz = gravity[2];
        }
        float n = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (n < 1f) return;
        gx /= n;
        gy /= n;
        gz /= n;
        float w = Math.abs(gy) / (Math.abs(gy) + Math.abs(gz) + 1e-6f);
        poseW = poseW * 0.92f + w * 0.08f;
    }

    private void handleGyro(SensorEvent e) {
        long ts = e.timestamp;
        if (lastGyroTs == 0) {
            lastGyroTs = ts;
            return;
        }
        float dt = (ts - lastGyroTs) / 1e9f;
        lastGyroTs = ts;
        if (dt <= 0 || dt > 0.1f) return;
        if (!mouseOn) return;

        float rx = (float) Math.toDegrees(e.values[0]);
        float ry = (float) Math.toDegrees(e.values[1]);
        float rz = (float) Math.toDegrees(e.values[2]);

        // ---- 零偏：长窗口(约2秒)中位数 + 分位差判定 ----
        // 手抖是零均值的，中位数依然能抓到真实零偏；分位差用来区分“真在转”
        hist[0][histIdx] = rx;
        hist[1][histIdx] = ry;
        hist[2][histIdx] = rz;
        histIdx = (histIdx + 1) % HIST;
        if (histFill < HIST) histFill++;
        frameCount++;
        if (histFill >= 60 && frameCount % 8 == 0) {
            float[] med = new float[3];
            boolean quiet = true;
            for (int ax = 0; ax < 3; ax++) {
                int n = histFill;
                float[] tmp = new float[n];
                System.arraycopy(hist[ax], 0, tmp, 0, n);
                java.util.Arrays.sort(tmp);
                med[ax] = tmp[n / 2];
                if (tmp[(int) (n * 0.9f)] - tmp[(int) (n * 0.1f)] > 8f) quiet = false;
            }
            if (quiet) {
                biasX += 0.15f * (med[0] - biasX);
                biasY += 0.15f * (med[1] - biasY);
                biasZ += 0.15f * (med[2] - biasZ);
            }
        }

        float wx = rx - biasX;
        float wy = ry - biasY;
        float wz = rz - biasZ;

        // ---- 3 点中值滤波：干掉单帧尖峰（快速抖动）----
        m3[0][m3Idx] = wx;
        m3[1][m3Idx] = wy;
        m3[2][m3Idx] = wz;
        m3Idx = (m3Idx + 1) % 3;
        if (m3Fill < 3) m3Fill++;
        if (m3Fill == 3) {
            wx = median3(m3[0]);
            wy = median3(m3[1]);
            wz = median3(m3[2]);
        }

        float mag = Math.max(Math.abs(wx), Math.max(Math.abs(wy), Math.abs(wz)));
        if (mag < 2.5f) {
            sH = sV = 0f;
            accX = accY = 0f;
            return;
        }

        float gate;
        if (mag < 7f) {
            gate = (mag - 2.5f) / 4.5f;
        } else {
            gate = 1f;
        }

        if (poseW > 0.6f) {
            useYaw = true;
        } else if (poseW < 0.4f) {
            useYaw = false;
        }

        float dVert = wx * dt * gate;
        float dHoriz = (useYaw ? wy : wz) * dt * gate;
        if (invertX) dHoriz = -dHoriz;
        if (invertY) dVert = -dVert;

        float a = smoothAlpha;
        sH = a * sH + (1 - a) * dHoriz;
        sV = a * sV + (1 - a) * dVert;

        float pxPerDeg = 8f + sensProgress / 100f * 52f;
        accX += sH * pxPerDeg;
        accY += sV * pxPerDeg;
        accX = Math.max(-60f, Math.min(60f, accX));
        accY = Math.max(-60f, Math.min(60f, accY));

        long now = SystemClock.uptimeMillis();
        if (now - lastMoveSent < 6) return;
        int dx = (int) accX;
        int dy = (int) accY;
        if (dx == 0 && dy == 0) return;
        accX -= dx;
        accY -= dy;
        lastMoveSent = now;
        try {
            sendRaw("mv " + dx + " " + dy);
        } catch (Exception ignored) {
        }
    }

    private static float median3(float[] a) {
        float x = a[0], y = a[1], z = a[2];
        if ((x <= y && y <= z) || (z <= y && y <= x)) return y;
        if ((y <= x && x <= z) || (z <= x && x <= y)) return x;
        return z;
    }
    // ================= SENSOR-CORE-END =================
```