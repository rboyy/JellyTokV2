package com.bytedance.tiktok.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bytedance.tiktok.R
import com.bytedance.tiktok.bean.CurUserBean
import com.bytedance.tiktok.bean.VideoBean
import com.bytedance.tiktok.jellyfin.JellyfinManager
import com.bytedance.tiktok.utils.RxBus
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init JellyfinManager
        JellyfinManager.init(this)

        // Try auto-login
        val creds = JellyfinManager.loadSavedCredentials()
        if (creds != null) {
            attemptAutoLogin(creds.first, creds.second, creds.third)
            return
        }

        showLogin()
    }

    private fun attemptAutoLogin(url: String, user: String, pass: String) {
        setContentView(R.layout.activity_login)
        findViewById<View>(R.id.et_server)?.let { (it as android.widget.EditText).setText(url) }
        findViewById<View>(R.id.et_username)?.let { (it as android.widget.EditText).setText(user) }
        findViewById<View>(R.id.progress)?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                JellyfinManager.login(url, user, pass)
                goToMain()
            } catch (e: Exception) {
                // Auto-login failed, show login screen with pre-filled fields
                findViewById<View>(R.id.progress)?.visibility = View.GONE
                setupLoginUI(url, user)
            }
        }
    }

    private fun showLogin() {
        setContentView(R.layout.activity_login)
        setupLoginUI("", "")
    }

    private fun setupLoginUI(savedUrl: String, savedUser: String) {
        val etServer = findViewById<android.widget.EditText>(R.id.et_server)
        val etUsername = findViewById<android.widget.EditText>(R.id.et_username)
        val etPassword = findViewById<android.widget.EditText>(R.id.et_password)
        val btnLogin = findViewById<View>(R.id.btn_login)
        val progress = findViewById<View>(R.id.progress)
        val tvError = findViewById<android.widget.TextView>(R.id.tv_error)

        if (savedUrl.isNotEmpty()) etServer.setText(savedUrl)
        if (savedUser.isNotEmpty()) etUsername.setText(savedUser)

        btnLogin.setOnClickListener {
            val url = etServer.text.toString().trim()
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString()

            if (url.isEmpty() || user.isEmpty()) {
                tvError.visibility = View.VISIBLE
                tvError.text = "请填写服务器地址和用户名"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            progress.visibility = View.VISIBLE
            tvError.visibility = View.GONE

            lifecycleScope.launch {
                try {
                    JellyfinManager.login(url, user, pass)
                    goToMain()
                } catch (e: Exception) {
                    btnLogin.isEnabled = true
                    progress.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = "登录失败: ${e.message}"
                }
            }
        }
    }

    private fun goToMain() {
        // Post default user bean for PersonalHomeFragment
        val userBean = VideoBean.UserBean()
        userBean.nickName = "Jellyfin"
        userBean.head = R.mipmap.head1
        userBean.sign = "JellyTok"
        RxBus.getDefault().post(CurUserBean(userBean))

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
