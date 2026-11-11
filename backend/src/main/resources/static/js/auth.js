// DevPilot Auth Helper (js/auth.js)

const Auth = {
    user: null,

    // Checks URL for ?error=oauth_failed or ?oauth=callback after GitHub redirect
    checkUrlParams() {
        const params = new URLSearchParams(window.location.search);
        const error = params.get("error");
        const isCallback = params.get("oauth") === "callback";

        if (error || isCallback) {
            window.history.replaceState({}, document.title, "/");
        }
        return { error, isCallback };
    },

    async fetchCurrentUser() {
        try {
            this.user = await Api.getMe();
            return this.user;
        } catch (err) {
            this.user = null;
            if (err.status === 401) return null;
            throw err;
        }
    },

    async loginWithGithub() {
        try {
            const data = await Api.getLoginUrl();
            window.location.href = (data && data.url) ? data.url : "/oauth2/authorization/github";
        } catch {
            window.location.href = "/oauth2/authorization/github";
        }
    },

    async logout() {
        await Api.logout();
        this.user = null;
    }
};
