package com.rad.bluetoothcar

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.redmadrobot.inputmask.MaskedTextChangedListener
import kotlinx.android.synthetic.main.activity_second.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    val mask: String by lazy { "[00]:[00]:[00]:[00]:[00]:[00]" }

    var xDiff: Double = 0.0
    var yDiff: Double = 0.0

    val handler: Handler by lazy { Handler() }

    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var outStream: OutputStream? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        checkBTState()
    }

    override fun onStart() {
        super.onStart()

        mac_field.setText("00:13:EF:00:7B:CF")

        MaskedTextChangedListener.installOn(
            mac_field,
            mask,
            object : MaskedTextChangedListener.ValueListener {
                override fun onTextChanged(
                    maskFilled: Boolean,
                    extractedValue: String,
                    formattedValue: String
                ) {
                }
            }
        )

        connect.setOnClickListener {
            btSocket?.let {
                disableBtConnection()
            } ?: if (mac_field.text.isNotBlank()) {
                tryToConnect(mac_field.text.toString())
            }
        }

        drawJoystick()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun drawJoystick() {
        val width = this.windowManager.defaultDisplay.width
        val height = this.windowManager.defaultDisplay.height

        val gameFrameSize = width
        val joystickSize = (gameFrameSize / 4)

        val joystick = ImageView(this)
        val lp = FrameLayout.LayoutParams(
            joystickSize,
            joystickSize
        )
        val startX = gameFrameSize / 2 - joystickSize / 2

        lp.setMargins(startX, startX, 0, 0)
        joystick.setBackgroundResource(R.drawable.circle_joystick)

        joystick.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    //save start tap position
                }
                MotionEvent.ACTION_MOVE -> {
                    //todo add circle barrier

                    val newX = event.rawX - view.width / 2
                    val newY = event.rawY - view.height / 2

                    val partX = event.rawX / (game_frame.width * .8)
                    val partY = event.rawY / (game_frame.height * .8)

                    xDiff = partX * 80 - 40
                    yDiff = -(partY * 80 - 40)

                    Log.i("TEST", "x = ${xDiff}")
                    Log.i("TEST", "y = ${yDiff}")

                    view.animate()
                        .x(newX)
                        .y(newY)
                        .setDuration(0)
                        .start()
                }
                MotionEvent.ACTION_UP -> {
                    //move joystick in center

                    xDiff = 0.0
                    yDiff = 0.0

                    view.animate()
                        .x(startX.toFloat())
                        .y(startX.toFloat())
                        .setDuration(0)
                        .start()
                }
            }
            true
        }


        game_frame.addView(joystick, lp)
    }

    public override fun onResume() {
        super.onResume()

        handler.postDelayed(object : Runnable {
            override fun run() {
                xDiff = if (xDiff >= 40) 40.0 else xDiff
                yDiff = if (yDiff >= 40) 40.0 else yDiff
                sendData("$$xDiff $yDiff;")
                handler.postDelayed(this, 100)
            }
        }, 100)
    }

    public override fun onPause() {
        super.onPause()
        disableBtConnection()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        game_frame.removeAllViews()
    }

    private fun tryToConnect(address: String) {
        Log.d(TAG, "...Попытка соединения...")

        btAdapter?.let { btAdapter ->
            val device = btAdapter.getRemoteDevice(address)

            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            btAdapter.cancelDiscovery()

            btSocket?.let {
                try {
                    Log.d(TAG, "...Соединяемся...")
                    it.connect()
                    Log.d(TAG, "...Соединение выполнено...")
                    Log.d(TAG, "...Создание Socket...")
                    outStream = it.outputStream
                    Log.d(TAG, "...Socket создан...")
                    Toast.makeText(this, "Подключение установлено!", Toast.LENGTH_LONG).show()
                    connect.text = "disconnect"
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка при подключении!", Toast.LENGTH_LONG).show()
                }
            } ?: Toast.makeText(this, "Ошибка при подключении!", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableBtConnection() {
        Log.d(TAG, "...Disable bt connect...")
        try {
            outStream?.flush()
        } catch (e: IOException) {
            e.message?.let {
                errorExit("Fatal Error", it)
            }
        }

        try {
            btSocket?.close()
        } catch (e2: IOException) {
            e2.message?.let {
                errorExit("Fatal Error", it)
            }
        }

        btSocket = null
        outStream = null

        connect.text = "connect"
    }

    private fun checkBTState() {
        btAdapter?.let {
            if (!it.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } ?: errorExit("Fatal Error", "Bluetooth не поддерживается")
    }

    private fun errorExit(title: String, message: String) {
        Toast.makeText(baseContext, "$title - $message", Toast.LENGTH_LONG).show()
//        finish()
    }

    private fun sendData(message: String) {
        outStream?.let {
            val msgBuffer = message.toByteArray()
            Log.d(TAG, "...Посылаем данные: $message...")
            try {
                it.write(msgBuffer)
            } catch (e: Exception) {
                e.message?.let { it1 -> errorExit("Fatal Error", it1) }
            }
        }
    }

    companion object {
        private const val TAG = "bluetooth"
        private const val REQUEST_ENABLE_BT = 1
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}