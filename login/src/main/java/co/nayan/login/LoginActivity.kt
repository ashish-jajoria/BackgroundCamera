package co.nayan.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import co.nayan.login.config.LoginConfig
import co.nayan.login.models.ActivityState
import co.nayan.login.models.ErrorState
import co.nayan.login.models.InitialState
import co.nayan.login.models.ProgressState
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_login.*
import org.koin.android.BuildConfig
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import retrofit2.HttpException
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val loginViewModel: LoginViewModel by viewModel()
    private val loginConfig: LoginConfig by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        buttonSubmit.setOnClickListener {
            loginViewModel.login(
                usernameInput.editText?.text.toString(),
                passwordInput.editText?.text.toString()
            )
        }

        if (intent.getBooleanExtra(SHOW_ERROR_MESSAGE, false)) {
            Snackbar.make(usernameInput, "You are not authorized to login", Snackbar.LENGTH_LONG)
                .show()
        }

        @SuppressLint("SetTextI18n")
        if (BuildConfig.DEBUG) {
            usernameInput.editText?.setText("user@example.com")
            passwordInput.editText?.setText("password")
        }

        loginViewModel.state.observe(this, stateObserver)
        if (intent.hasExtra(VERSION_NAME) && intent.hasExtra(VERSION_CODE)) {
            buildVersionTv.text = String.format(
                "v %s.%d",
                intent.getStringExtra(VERSION_NAME),
                intent.getIntExtra(VERSION_CODE, 0)
            )
        }
    }

    @SuppressLint("WrongConstant")
    private val stateObserver: Observer<ActivityState> = Observer {
        when (it) {
            InitialState -> {
                usernameInput.editText?.isEnabled = true
                passwordInput.editText?.isEnabled = true
                buttonSubmit.isEnabled = true
            }
            ProgressState -> {
                usernameInput.editText?.isEnabled = false
                passwordInput.editText?.isEnabled = false
                buttonSubmit.isEnabled = false
            }
            is LoginViewModel.LoginSuccessState -> {
                val intent = Intent(this@LoginActivity, loginConfig.mainActivityClass())
                intent.putExtra(KEY_USER, it.user)
                finish()
                startActivity(intent)
            }
            is ErrorState -> {
                usernameInput.editText?.isEnabled = true
                passwordInput.editText?.isEnabled = true
                buttonSubmit.isEnabled = true

                val errorMessage = when (it.exception) {
                    is HttpException -> {
                        getString(R.string.could_not_login)
                    }
                    is IOException -> {
                        it.exception.message.toString()
                    }
                    else -> {
                        getString(R.string.something_went_wrong)
                    }
                }

                Snackbar.make(buttonSubmit, errorMessage, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val KEY_USER = "user"
        const val VERSION_NAME = "VERSION_NAME"
        const val VERSION_CODE = "VERSION_CODE"
        const val SHOW_ERROR_MESSAGE = "show_error_message"
    }
}